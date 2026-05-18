import React, { useEffect, useRef } from 'react';
import { View, Animated, StyleSheet, ViewStyle } from 'react-native';

const shimmerBaseColor = '#e2e8f0';
const shimmerHighlightColor = '#f8fafc';

function useShimmer(width: number) {
  const anim = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    const loop = Animated.loop(
      Animated.timing(anim, {
        toValue: 1,
        duration: 1200,
        useNativeDriver: true,
      })
    );
    loop.start();
    return () => loop.stop();
  }, [anim]);

  const translateX = anim.interpolate({
    inputRange: [0, 1],
    outputRange: [-width, width],
  });

  return { translateX };
}

export function SkeletonLine({ width = '100%', height = 14, style }: { width?: string | number; height?: number; style?: ViewStyle }) {
  const w = typeof width === 'string' ? 300 : width;
  const { translateX } = useShimmer(w);
  return (
    <View style={[{ width, height, backgroundColor: shimmerBaseColor, borderRadius: 4, overflow: 'hidden' }, style]}>
      <Animated.View
        style={[
          StyleSheet.absoluteFill,
          {
            transform: [{ translateX }],
            backgroundColor: shimmerHighlightColor,
            opacity: 0.5,
            width: '40%',
          },
        ]}
      />
    </View>
  );
}

export function SkeletonCard() {
  return (
    <View style={styles.card}>
      <SkeletonLine width={80} height={80} style={styles.thumb} />
      <View style={styles.body}>
        <SkeletonLine width="70%" height={16} style={styles.mb8} />
        <SkeletonLine width="40%" height={12} style={styles.mb8} />
        <SkeletonLine width="90%" height={12} />
      </View>
    </View>
  );
}

export function SkeletonList({ count = 5 }: { count?: number }) {
  return (
    <>
      {Array.from({ length: count }).map((_, i) => (
        <SkeletonCard key={i} />
      ))}
    </>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    padding: 16,
    backgroundColor: '#fff',
    borderRadius: 12,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 3,
    elevation: 2,
  },
  thumb: { borderRadius: 8 },
  body: { flex: 1, marginLeft: 12, justifyContent: 'center' },
  mb8: { marginBottom: 8 },
});
