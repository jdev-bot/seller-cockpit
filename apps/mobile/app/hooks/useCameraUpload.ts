import { useState, useCallback } from 'react';
import * as ImagePicker from 'expo-image-picker';
import { useApi } from './useApi';

export interface UploadProgress {
  assetId: string;
  status: 'pending' | 'uploading' | 'processing' | 'done' | 'error';
  progress: number; // 0-100
  error?: string;
}

const MIME_TYPES: Record<string, string> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  heic: 'image/heic',
  mp4: 'video/mp4',
  mov: 'video/quicktime',
};

export function useCameraUpload(productCaseId?: string) {
  const api = useApi();
  const [uploads, setUploads] = useState<Record<string, UploadProgress>>({});

  const pickAndUpload = useCallback(async (
    source: 'camera' | 'gallery',
    options: ImagePicker.ImagePickerOptions = {}
  ) => {
    if (!productCaseId) throw new Error('No product case selected');

    // Request permissions
    const permFn = source === 'camera'
      ? ImagePicker.requestCameraPermissionsAsync
      : ImagePicker.requestMediaLibraryPermissionsAsync;
    const perm = await permFn();
    if (!perm.granted) {
      throw new Error(`${source} permission denied`);
    }

    // Launch picker
    const pickerFn = source === 'camera'
      ? ImagePicker.launchCameraAsync
      : ImagePicker.launchImageLibraryAsync;

    const result = await pickerFn({
      mediaTypes: ImagePicker.MediaTypeOptions.All,
      allowsEditing: true,
      aspect: [4, 3],
      quality: 0.85,
      ...options,
    });

    if (result.canceled || !result.assets?.length) return null;
    const asset = result.assets[0];
    const uri = asset.uri;
    const fileName = asset.fileName || `upload_${Date.now()}.jpg`;
    const ext = fileName.split('.').pop()?.toLowerCase() || 'jpg';
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    const uploadId = `${productCaseId}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    setUploads(prev => ({
      ...prev,
      [uploadId]: { assetId: uploadId, status: 'pending', progress: 0 },
    }));

    try {
      // 1. Get presigned URL
      setUploads(prev => ({ ...prev, [uploadId]: { ...prev[uploadId]!, status: 'uploading', progress: 5 } }));
      const { uploadUrl, storageUrl, mediaAssetId } = await api.getUploadUrl(
        productCaseId, fileName, contentType
      );

      // 2. Upload to MinIO/S3 via presigned URL
      setUploads(prev => ({ ...prev, [uploadId]: { ...prev[uploadId]!, progress: 15 } }));

      // Read file as blob/arrayBuffer
      const response = await fetch(uri);
      const blob = await response.blob();

      // Upload with progress tracking (using XMLHttpRequest for progress events)
      await new Promise<void>((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.upload.addEventListener('progress', (event) => {
          if (event.lengthComputable) {
            const pct = Math.round(15 + (event.loaded / event.total) * 70); // 15-85%
            setUploads(prev => ({
              ...prev,
              [uploadId]: { ...prev[uploadId]!, progress: pct },
            }));
          }
        });
        xhr.addEventListener('load', () => {
          if (xhr.status >= 200 && xhr.status < 300) resolve();
          else reject(new Error(`Upload failed: ${xhr.status}`));
        });
        xhr.addEventListener('error', () => reject(new Error('Upload network error')));
        xhr.open('PUT', uploadUrl);
        xhr.setRequestHeader('Content-Type', contentType);
        xhr.send(blob);
      });

      // 3. Confirm upload to backend
      setUploads(prev => ({ ...prev, [uploadId]: { ...prev[uploadId]!, progress: 90 } }));
      await api.confirmUpload(productCaseId, storageUrl);

      setUploads(prev => ({
        ...prev,
        [uploadId]: { ...prev[uploadId]!, status: 'done', progress: 100 },
      }));

      return { mediaAssetId, storageUrl, uri };
    } catch (err: any) {
      setUploads(prev => ({
        ...prev,
        [uploadId]: { ...prev[uploadId]!, status: 'error', error: err.message || 'Upload failed' },
      }));
      throw err;
    }
  }, [api, productCaseId]);

  const pickFromCamera = useCallback(() => pickAndUpload('camera'), [pickAndUpload]);
  const pickFromGallery = useCallback(() => pickAndUpload('gallery'), [pickAndUpload]);

  return {
    pickFromCamera,
    pickFromGallery,
    uploads,
  };
}
