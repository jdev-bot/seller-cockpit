import { useState, useCallback, useRef } from 'react';
import * as FileSystem from 'expo-file-system';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8080';

export interface UploadProgress {
  fileName: string;
  progress: number; // 0-100
  status: 'pending' | 'uploading' | 'done' | 'error';
  error?: string;
}

export function useBackgroundUpload() {
  const [uploads, setUploads] = useState<Record<string, UploadProgress>>({});
  const uploadRef = useRef<FileSystem.FileSystemNetworkTaskProgressCallback>();

  const uploadMedia = useCallback(async (productCaseId: string, uri: string, fileName: string) => {
    setUploads(prev => ({
      ...prev,
      [fileName]: { fileName, progress: 0, status: 'uploading' }
    }));

    try {
      // 1. Get presigned upload URL from backend
      const res = await fetch(`${API_BASE_URL}/api/product-cases/${productCaseId}/media/upload-url`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fileName, size: await FileSystem.getInfoAsync(uri).then((info: any) => info.size || 0) }),
      });

      if (!res.ok) throw new Error('Failed to get upload URL');
      const { uploadUrl, thumbnailUrl, mediaAssetId } = await res.json();

      // 2. Upload to MinIO/S3 using Expo FileSystem (background-capable)
      const uploadRes = await FileSystem.uploadAsync(uploadUrl, uri, {
        httpMethod: 'PUT',
        uploadType: FileSystem.FileSystemUploadType.BINARY_CONTENT,
        headers: { 'Content-Type': 'application/octet-stream' },
      });

      if (uploadRes.status >= 200 && uploadRes.status < 300) {
        // 3. Confirm upload completion
        await fetch(`${API_BASE_URL}/api/product-cases/${productCaseId}/media/complete`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ mediaAssetId }),
        });

        setUploads(prev => ({
          ...prev,
          [fileName]: { fileName, progress: 100, status: 'done' }
        }));
        return { mediaAssetId, thumbnailUrl };
      } else {
        throw new Error(`Upload failed with status ${uploadRes.status}`);
      }
    } catch (e: any) {
      setUploads(prev => ({
        ...prev,
        [fileName]: { fileName, progress: 0, status: 'error', error: e.message }
      }));
      throw e;
    }
  }, []);

  const resetUpload = useCallback((fileName: string) => {
    setUploads(prev => {
      const next = { ...prev };
      delete next[fileName];
      return next;
    });
  }, []);

  return { uploads, uploadMedia, resetUpload };
}
