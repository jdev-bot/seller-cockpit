import { useState, useRef, useCallback, useEffect } from 'react';
import { DeviceMotion } from 'expo-sensors';
import { Subscription } from 'expo-sensors/build/DeviceSensor';

export interface CoverageGrid {
  rows: number;
  cols: number;
  cells: boolean[]; // flattened row-major: row*cols + col
}

export interface MissingRegion {
  row: number;
  col: number;
  label: string;
}

export interface ScanCoverageState {
  coverageGrid: CoverageGrid;
  coveragePercent: number;
  missingRegions: MissingRegion[];
  isScanComplete: boolean;
  isScanning: boolean;
  elapsedMs: number;
  autoStopDelayMs: number;
}

const GRID_ROWS = 4;
const GRID_COLS = 6;
const TOTAL_CELLS = GRID_ROWS * GRID_COLS;

const PITCH_MIN = -45; // degrees, looking down
const PITCH_MAX = 45;  // degrees, looking up
const ROLL_MIN = -60;  // degrees, looking left
const ROLL_MAX = 60;   // degrees, looking right

// Pre-compute region labels for user-friendly guidance
const REGION_LABELS: string[][] = [
  ['Top-Left',    'Top-Center-Left',  'Top-Center',  'Top-Center-Right',  'Top-Right'],
  ['Upper-Left',  'Upper-Center-Left','Upper-Center','Upper-Center-Right','Upper-Right'],
  ['Lower-Left',  'Lower-Center-Left','Lower-Center','Lower-Center-Right','Lower-Right'],
  ['Bottom-Left', 'Bottom-Center-Left','Bottom-Center','Bottom-Center-Right','Bottom-Right'],
];

function radToDeg(rad: number): number {
  return (rad * 180) / Math.PI;
}

function clamp(val: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, val));
}

function mapToGrid(pitchDeg: number, rollDeg: number): { row: number; col: number } | null {
  if (pitchDeg < PITCH_MIN || pitchDeg > PITCH_MAX || rollDeg < ROLL_MIN || rollDeg > ROLL_MAX) {
    return null; // outside scan cone
  }
  const row = Math.floor(((pitchDeg - PITCH_MIN) / (PITCH_MAX - PITCH_MIN)) * GRID_ROWS);
  const col = Math.floor(((rollDeg - ROLL_MIN) / (ROLL_MAX - ROLL_MIN)) * GRID_COLS);
  return {
    row: clamp(row, 0, GRID_ROWS - 1),
    col: clamp(col, 0, GRID_COLS - 1),
  };
}

export function useScanCoverage(autoStopThreshold = 95, autoStopHoldMs = 2000) {
  const [state, setState] = useState<ScanCoverageState>({
    coverageGrid: { rows: GRID_ROWS, cols: GRID_COLS, cells: Array(TOTAL_CELLS).fill(false) },
    coveragePercent: 0,
    missingRegions: [],
    isScanComplete: false,
    isScanning: false,
    elapsedMs: 0,
    autoStopDelayMs: autoStopHoldMs,
  });

  const motionSub = useRef<Subscription | null>(null);
  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const elapsedTimerRef = useRef<NodeJS.Timeout | null>(null);
  const startTimeRef = useRef<number>(0);
  const aboveThresholdSinceRef = useRef<number | null>(null);
  const currentGridRef = useRef<boolean[]>(Array(TOTAL_CELLS).fill(false));

  const updateCoverage = useCallback((pitchDeg: number, rollDeg: number) => {
    const gridPos = mapToGrid(pitchDeg, rollDeg);
    if (!gridPos) return;

    const idx = gridPos.row * GRID_COLS + gridPos.col;
    const grid = currentGridRef.current;
    if (!grid[idx]) {
      grid[idx] = true;
      const covered = grid.filter(Boolean).length;
      const percent = Math.round((covered / TOTAL_CELLS) * 100);

      const missing: MissingRegion[] = [];
      for (let r = 0; r < GRID_ROWS; r++) {
        for (let c = 0; c < GRID_COLS; c++) {
          if (!grid[r * GRID_COLS + c]) {
            missing.push({ row: r, col: c, label: REGION_LABELS[r]?.[c] || `R${r}C${c}` });
          }
        }
      }

      setState(prev => ({
        ...prev,
        coverageGrid: { rows: GRID_ROWS, cols: GRID_COLS, cells: [...grid] },
        coveragePercent: percent,
        missingRegions: missing,
      }));
    }
  }, []);

  const startScanning = useCallback(async () => {
    // Reset state
    const freshGrid = Array(TOTAL_CELLS).fill(false);
    currentGridRef.current = freshGrid;
    aboveThresholdSinceRef.current = null;
    startTimeRef.current = Date.now();

    setState({
      coverageGrid: { rows: GRID_ROWS, cols: GRID_COLS, cells: freshGrid },
      coveragePercent: 0,
      missingRegions: [],
      isScanComplete: false,
      isScanning: true,
      elapsedMs: 0,
      autoStopDelayMs: autoStopHoldMs,
    });

    // Subscribe to device motion
    await DeviceMotion.setUpdateInterval(100); // 10Hz
    motionSub.current = DeviceMotion.addListener((motion) => {
      // Use rotation.beta (pitch) and rotation.gamma (roll) if available
      // Fallback: derive from accelerationIncludingGravity
      let pitchDeg = 0;
      let rollDeg = 0;

      if (motion.rotation) {
        pitchDeg = radToDeg(motion.rotation.beta || 0);
        rollDeg = radToDeg(motion.rotation.gamma || 0);
      } else if (motion.accelerationIncludingGravity) {
        const { x = 0, y = 0, z = 0 } = motion.accelerationIncludingGravity;
        const norm = Math.sqrt(x * x + y * y + z * z) || 1;
        pitchDeg = radToDeg(Math.asin(clamp(y / norm, -1, 1)));
        rollDeg = radToDeg(Math.asin(clamp(-x / norm, -1, 1)));
      }

      updateCoverage(pitchDeg, rollDeg);
    });

    // Elapsed timer
    elapsedTimerRef.current = setInterval(() => {
      const elapsed = Date.now() - startTimeRef.current;
      setState(prev => {
        const newState = { ...prev, elapsedMs: elapsed };

        // Auto-stop logic
        if (prev.coveragePercent >= autoStopThreshold) {
          if (aboveThresholdSinceRef.current === null) {
            aboveThresholdSinceRef.current = Date.now();
          } else {
            const held = Date.now() - aboveThresholdSinceRef.current;
            if (held >= autoStopHoldMs && !prev.isScanComplete) {
              newState.isScanComplete = true;
              newState.isScanning = false;
              // Clean up in next tick
              setTimeout(() => stopScanning(), 0);
            }
          }
        } else {
          aboveThresholdSinceRef.current = null;
        }

        return newState;
      });
    }, 250);
  }, [updateCoverage, autoStopThreshold, autoStopHoldMs]);

  const stopScanning = useCallback(() => {
    if (motionSub.current) {
      motionSub.current.remove();
      motionSub.current = null;
    }
    if (elapsedTimerRef.current) {
      clearInterval(elapsedTimerRef.current);
      elapsedTimerRef.current = null;
    }
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    setState(prev => ({
      ...prev,
      isScanning: false,
      isScanComplete: prev.coveragePercent >= autoStopThreshold,
    }));
  }, [autoStopThreshold]);

  const resetCoverage = useCallback(() => {
    stopScanning();
    const freshGrid = Array(TOTAL_CELLS).fill(false);
    currentGridRef.current = freshGrid;
    aboveThresholdSinceRef.current = null;
    setState({
      coverageGrid: { rows: GRID_ROWS, cols: GRID_COLS, cells: freshGrid },
      coveragePercent: 0,
      missingRegions: [],
      isScanComplete: false,
      isScanning: false,
      elapsedMs: 0,
      autoStopDelayMs: autoStopHoldMs,
    });
  }, [stopScanning, autoStopHoldMs]);

  useEffect(() => {
    return () => {
      if (motionSub.current) motionSub.current.remove();
      if (elapsedTimerRef.current) clearInterval(elapsedTimerRef.current);
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  return {
    ...state,
    startScanning,
    stopScanning,
    resetCoverage,
  };
}
