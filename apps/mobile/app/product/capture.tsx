import React, { useState, useRef, useCallback } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Image, ScrollView, ActivityIndicator, Alert } from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as ImagePicker from 'expo-image-picker';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useApi } from '../hooks/useApi';

export default function CaptureScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const api = useApi();
  const [permission, requestPermission] = useCameraPermissions();
  const [mode, setMode] = useState<'camera' | 'gallery'>('camera');
  const [captured, setCaptured] = useState<string[]>([]);
  const [uploading, setUploading] = useState(false);
  const cameraRef = useRef<CameraView | null>(null);

  const onSnap = useCallback(async () => {
    if (!cameraRef.current) return;
    const photo = await cameraRef.current.takePictureAsync({ quality: 0.8 });
    if (photo?.uri) setCaptured((prev) => [...prev, photo.uri]);
  }, []);

  const onPick = useCallback(async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ImagePicker.MediaTypeOptions.All,
      allowsMultipleSelection: true,
      quality: 0.8,
    });
    if (!result.canceled && result.assets) {
      setCaptured((prev) => [...prev, ...result.assets.map((a) => a.uri)]);
    }
  }, []);

  const onUpload = useCallback(async () => {
    if (!id || captured.length === 0) return;
    setUploading(true);
    try {
      for (const uri of captured) {
        const ext = uri.split('.').pop() || 'jpg';
        const contentType = ext === 'mp4' || ext === 'mov' ? 'video/mp4' : 'image/jpeg';
        const filename = `media-${Date.now()}.${ext}`;
        const { uploadUrl, mediaAssetId } = await api.getUploadUrl(id, filename, contentType);
        // Upload binary to presigned URL
        const blob = await (await fetch(uri)).blob();
        await fetch(uploadUrl, { method: 'PUT', body: blob, headers: { 'Content-Type': contentType } });
      }
      // Trigger processing
      await api.processMedia(id);
      router.push(`/product/${id}`);
    } catch (e: any) {
      Alert.alert('Upload failed', e.message || 'Unknown error');
    } finally {
      setUploading(false);
    }
  }, [id, captured, api, router]);

  if (!permission) return <View style={styles.center}><ActivityIndicator /></View>;
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
        <TouchableOpacity style={[styles.tab, mode === 'camera' && styles.tabActive]} onPress={() => setMode('camera')}>
          <Text style={styles.tabText}>Camera</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.tab, mode === 'gallery' && styles.tabActive]} onPress={() => { setMode('gallery'); onPick(); }}>
          <Text style={styles.tabText}>Gallery</Text>
        </TouchableOpacity>
      </View>

      {mode === 'camera' && (
        <View style={styles.cameraWrap}>
          <CameraView style={styles.camera} ref={cameraRef} mode="picture">
            <View style={styles.cameraOverlay}>
              <TouchableOpacity style={styles.shutter} onPress={onSnap}>
                <View style={styles.shutterInner} />
              </TouchableOpacity>
            </View>
          </CameraView>
        </View>
      )}

      <ScrollView horizontal contentContainerStyle={styles.thumbs} showsHorizontalScrollIndicator={false}>
        {captured.map((uri, idx) => (
          <View key={idx} style={styles.thumbWrap}>
            <Image source={{ uri }} style={styles.thumb} />
            <TouchableOpacity style={styles.thumbRemove} onPress={() => setCaptured((p) => p.filter((_, i) => i !== idx))}>
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
        <Text style={styles.btnText}>{uploading ? 'Uploading...' : `Upload ${captured.length} media`}</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#000' },
  permText: { color: '#fff', fontSize: 16, marginBottom: 12 },
  tabs: { flexDirection: 'row', justifyContent: 'center', paddingTop: 12, paddingBottom: 6, backgroundColor: '#111827' },
  tab: { paddingHorizontal: 20, paddingVertical: 6, borderRadius: 16, marginHorizontal: 6 },
  tabActive: { backgroundColor: '#2563eb' },
  tabText: { color: '#ffffff', fontWeight: '600', fontSize: 14 },
  cameraWrap: { flex: 1 },
  camera: { flex: 1 },
  cameraOverlay: { position: 'absolute', bottom: 30, left: 0, right: 0, alignItems: 'center' },
  shutter: { width: 72, height: 72, borderRadius: 36, backgroundColor: '#ffffff', alignItems: 'center', justifyContent: 'center' },
  shutterInner: { width: 56, height: 56, borderRadius: 28, borderWidth: 3, borderColor: '#111827' },
  thumbs: { padding: 10, gap: 8 },
  thumbWrap: { position: 'relative' },
  thumb: { width: 72, height: 72, borderRadius: 8, backgroundColor: '#333' },
  thumbRemove: { position: 'absolute', top: 2, right: 2, width: 20, height: 20, borderRadius: 10, backgroundColor: '#ef4444', alignItems: 'center', justifyContent: 'center' },
  thumbRemoveText: { color: '#ffffff', fontWeight: '700', fontSize: 12 },
  btn: { margin: 16, backgroundColor: '#2563eb', paddingVertical: 14, borderRadius: 10, alignItems: 'center' },
  btnDisabled: { opacity: 0.5 },
  btnText: { color: '#ffffff', fontWeight: '700', fontSize: 15 },
});
