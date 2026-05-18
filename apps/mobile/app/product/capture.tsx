import React, { useState, useRef, useCallback, useEffect } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, Image, ScrollView,
  ActivityIndicator, Alert, Dimensions,
} from 'react-native';
import { CameraView, useCameraPermissions, CameraRecordingOptions } from 'expo-camera';
import * as ImagePicker from 'expo-image-picker';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';
import { useScanCoverage } from '../hooks/useScanCoverage';
import ScanOverlay from '../components/ScanOverlay';

const { height: SCREEN_H } = Dimensions.get('window');

export default function CaptureScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [permission, requestPermission] = useCameraPermissions();
  const [mode, setMode] = useState<'video' | 'gallery'>('video');
  const [captured, setCaptured] = useState<{ uri: string; type: 'photo' | 'video' }[]>([]);
  const [uploading, setUploading] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const cameraRef = useRef<CameraView | null>(null);

  const {
    coverageGrid,
    coveragePercent,
    missingRegions,
    isScanComplete,
    isScanning,
    elapsedMs,
    autoStopDelayMs,
    startScanning,
    stopScanning,
    resetCoverage,
  } = useScanCoverage(95, 2000);

  // Auto-stop recording when scan completes + upload coverage
  useEffect(() => {
    if (isScanComplete && isRecording) {
      stopRecording();
    }
  }, [isScanComplete, isRecording]);

  // Upload coverage data when scan stops
  useEffect(() => {
    if (!isScanning && coveragePercent > 0 && id) {
      api.saveScanCoverage(id, {
        gridRows: coverageGrid.rows,
        gridCols: coverageGrid.cols,
        coveredCells: coverageGrid.cells.filter(Boolean).length,
        totalCells: coverageGrid.cells.length,
        coveragePercent,
        elapsedMs,
        isComplete: isScanComplete,
        autoStopped: isScanComplete,
        cellData: coverageGrid.cells,
        missingRegions: missingRegions.map(r => ({ row: r.row, col: r.col, label: r.label })),
      }).catch((e) => console.warn('Coverage upload failed:', e));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isScanning]);

  const startRecording = useCallback(async () => {
    if (!cameraRef.current) return;
    try {
      setIsRecording(true);
      await startScanning();

      const options: CameraRecordingOptions = {
        maxDuration: 60,
      };
      const video = await cameraRef.current.recordAsync(options);
      if (video?.uri) {
        setCaptured(prev => [...prev, { uri: video.uri, type: 'video' }]);
      }
    } catch (e: any) {
      console.warn('Recording error:', e);
      Alert.alert('Recording failed', e.message || 'Could not start recording');
    } finally {
      setIsRecording(false);
      stopScanning();
    }
  }, [startScanning, stopScanning]);

  const stopRecording = useCallback(() => {
    if (cameraRef.current && isRecording) {
      cameraRef.current.stopRecording();
    }
    setIsRecording(false);
    stopScanning();
  }, [isRecording, stopScanning]);

  const onSnapPhoto = useCallback(async () => {
    if (!cameraRef.current) return;
    const photo = await cameraRef.current.takePictureAsync({ quality: 0.85 });
    if (photo?.uri) {
      setCaptured(prev => [...prev, { uri: photo.uri, type: 'photo' }]);
    }
  }, []);

  const onPickGallery = useCallback(async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.All,
      allowsMultipleSelection: true,
      quality: 0.85,
    });
    if (!result.canceled && result.assets) {
      setCaptured(prev => [
        ...prev,
        ...result.assets.map((a) => ({
          uri: a.uri,
          type: (a.type === 'video' ? 'video' : 'photo') as 'photo' | 'video',
        })),
      ]);
    }
  }, []);

  const onUpload = useCallback(async () => {
    if (!id || captured.length === 0) return;
    setUploading(true);
    try {
      for (const item of captured) {
        const ext = item.uri.split('.').pop() || 'jpg';
        const contentType = item.type === 'video' || ext === 'mp4' || ext === 'mov'
          ? 'video/mp4'
          : 'image/jpeg';
        const filename = `media-${Date.now()}.${ext}`;
        const { uploadUrl } = await api.getUploadUrl(id, filename, contentType);
        const blob = await (await fetch(item.uri)).blob();
        await fetch(uploadUrl, { method: 'PUT', body: blob, headers: { 'Content-Type': contentType } });
      }
      await api.processMedia(id);
      router.push(`/product/${id}`);
    } catch (e: any) {
      Alert.alert('Upload failed', e.message || 'Unknown error');
    } finally {
      setUploading(false);
    }
  }, [id, captured, api, router]);

  const removeCaptured = useCallback((idx: number) => {
    setCaptured(prev => prev.filter((_, i) => i !== idx));
  }, []);

  if (!permission) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color="#fff" />
      </View>
    );
  }
  if (!permission.granted) {
    return (
      <View style={styles.center}>
        <Text style={styles.permText}>Camera permission is needed.</Text>
        <TouchableOpacity style={styles.btn} onPress={requestPermission}>
          <Text style={styles.btnText}>Grant Permission</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.tabs}>
        <TouchableOpacity
          style={[styles.tab, mode === 'video' && styles.tabActive]}
          onPress={() => { setMode('video'); resetCoverage(); }}
        >
          <Text style={styles.tabText}>Scan Video</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tab, mode === 'gallery' && styles.tabActive]}
          onPress={() => { setMode('gallery'); onPickGallery(); }}
        >
          <Text style={styles.tabText}>Gallery</Text>
        </TouchableOpacity>
      </View>

      {mode === 'video' && (
        <>
          <View style={styles.cameraWrap}>
            <CameraView
              style={styles.camera}
              ref={cameraRef}
              mode="video"
              videoQuality="720p"
            >
              <ScanOverlay
                coverageGrid={coverageGrid}
                coveragePercent={coveragePercent}
                missingRegions={missingRegions}
                isScanning={isScanning}
                elapsedMs={elapsedMs}
                autoStopDelayMs={autoStopDelayMs}
              />

              <View style={styles.controlsOverlay}>
                {!isRecording ? (
                  <View style={styles.controlRow}>
                    <TouchableOpacity style={styles.photoBtn} onPress={onSnapPhoto}>
                      <Text style={styles.photoBtnText}>📷</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={styles.recordBtn} onPress={startRecording}>
                      <View style={styles.recordInner} />
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={styles.reviewBtn}
                      onPress={() => router.push(`/product/scan-coverage?productCaseId=${id}`)}
                      disabled={coveragePercent === 0}
                    >
                      <Text style={[styles.reviewBtnText, coveragePercent === 0 && { opacity: 0.4 }]}>
                        Review
                      </Text>
                    </TouchableOpacity>
                  </View>
                ) : (
                  <View style={styles.controlRow}>
                    <View style={styles.recordingIndicator}>
                      <View style={styles.recDot} />
                      <Text style={styles.recText}>REC {formatTime(elapsedMs)}</Text>
                    </View>
                    <TouchableOpacity style={styles.stopBtn} onPress={stopRecording}>
                      <View style={styles.stopInner} />
                    </TouchableOpacity>
                  </View>
                )}
              </View>
            </CameraView>
          </View>

          <View style={styles.progressBarContainer}>
            <View style={[styles.progressBar, { width: `${coveragePercent}%` }]}>
              {coveragePercent >= 95 && <View style={styles.progressPulse} />}
            </View>
          </View>
        </>
      )}

      <ScrollView horizontal contentContainerStyle={styles.thumbs} showsHorizontalScrollIndicator={false}>
        {captured.map((item, idx) => (
          <View key={idx} style={styles.thumbWrap}>
            <Image source={{ uri: item.uri }} style={styles.thumb} />
            {item.type === 'video' && (
              <View style={styles.videoBadge}>
                <Text style={styles.videoBadgeText}>▶</Text>
              </View>
            )}
            <TouchableOpacity style={styles.thumbRemove} onPress={() => removeCaptured(idx)}>
              <Text style={styles.thumbRemoveText}>×</Text>
            </TouchableOpacity>
          </View>
        ))}
      </ScrollView>

      <TouchableOpacity
        style={[styles.btn, (captured.length === 0 || uploading) && styles.btnDisabled]}
        onPress={onUpload}
        disabled={captured.length === 0 || uploading}
      >
        <Text style={styles.btnText}>
          {uploading ? 'Uploading…' : `Upload ${captured.length} item${captured.length !== 1 ? 's' : ''}`}
        </Text>
      </TouchableOpacity>
    </View>
  );
}

function formatTime(ms: number) {
  const s = Math.floor(ms / 1000);
  const m = Math.floor(s / 60);
  const rem = s % 60;
  return `${m}:${rem.toString().padStart(2, '0')}`;
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#000' },
  permText: { color: '#fff', fontSize: 16, marginBottom: 12 },
  tabs: {
    flexDirection: 'row',
    justifyContent: 'center',
    paddingTop: 12,
    paddingBottom: 6,
    backgroundColor: '#111827',
  },
  tab: { paddingHorizontal: 20, paddingVertical: 6, borderRadius: 16, marginHorizontal: 6 },
  tabActive: { backgroundColor: '#2563eb' },
  tabText: { color: '#ffffff', fontWeight: '600', fontSize: 14 },
  cameraWrap: {
    flex: 1,
    position: 'relative',
    overflow: 'hidden',
  },
  camera: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  controlsOverlay: {
    position: 'absolute',
    bottom: 24,
    left: 0,
    right: 0,
    alignItems: 'center',
  },
  controlRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 24,
  },
  photoBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,255,255,0.2)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  photoBtnText: { fontSize: 20 },
  recordBtn: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  recordInner: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#ef4444',
  },
  stopBtn: {
    width: 72,
    height: 72,
    borderRadius: 8,
    backgroundColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  stopInner: {
    width: 28,
    height: 28,
    borderRadius: 4,
    backgroundColor: '#ef4444',
  },
  reviewBtn: {
    width: 56,
    height: 44,
    borderRadius: 8,
    backgroundColor: 'rgba(255,255,255,0.2)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  reviewBtnText: { color: '#ffffff', fontWeight: '600', fontSize: 12 },
  recordingIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.6)',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    marginBottom: 12,
  },
  recDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: '#ef4444',
    marginRight: 8,
  },
  recText: {
    color: '#ffffff',
    fontWeight: '700',
    fontSize: 14,
    fontVariant: ['tabular-nums'],
  },
  progressBarContainer: {
    height: 4,
    backgroundColor: 'rgba(255,255,255,0.15)',
    width: '100%',
  },
  progressBar: {
    height: 4,
    backgroundColor: '#22c55e',
    borderRadius: 2,
  },
  progressPulse: {
    position: 'absolute',
    right: 0,
    top: -2,
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#a7f3d0',
  },
  thumbs: { padding: 10, gap: 8 },
  thumbWrap: { position: 'relative' },
  thumb: { width: 72, height: 72, borderRadius: 8, backgroundColor: '#333' },
  videoBadge: {
    position: 'absolute',
    bottom: 4,
    left: 4,
    backgroundColor: 'rgba(0,0,0,0.6)',
    paddingHorizontal: 4,
    paddingVertical: 2,
    borderRadius: 4,
  },
  videoBadgeText: { color: '#fff', fontSize: 10 },
  thumbRemove: {
    position: 'absolute',
    top: 2,
    right: 2,
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: '#ef4444',
    alignItems: 'center',
    justifyContent: 'center',
  },
  thumbRemoveText: { color: '#ffffff', fontWeight: '700', fontSize: 12 },
  btn: {
    margin: 16,
    backgroundColor: '#2563eb',
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  btnDisabled: { opacity: 0.5 },
  btnText: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
});
