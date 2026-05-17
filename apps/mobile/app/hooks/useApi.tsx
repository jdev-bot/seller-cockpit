import React, { createContext, useContext, useState, useCallback } from 'react';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_URL || 'http://localhost:8080';

export interface ProductCase {
  id: string;
  userId: string;
  sellerMode: 'PRIVATE_DECLUTTERING' | 'PRIVATE_RESELLING' | 'PROFESSIONAL';
  status: string;
  title?: string;
  productFacts?: {
    title: string;
    brand?: string;
    model?: string;
    category?: string;
    variant?: string;
    color?: string;
    sizeOrCapacity?: string;
    accessories: string[];
    userConfirmed: boolean;
    confidence: number;
  };
  conditionAssessment?: {
    condition: string;
    visibleDefects: string[];
    functionalityConfirmed?: boolean;
    missingInformation: string[];
    confidence: number;
    userConfirmed: boolean;
  };
  pricingProfile?: any;
  pricingRecommendation?: any;
  marketResearchResult?: any;
  missingQuestions: string[];
  complianceWarnings: string[];
  createdAt: string;
  updatedAt: string;
}

export interface DashboardItem {
  id: string;
  title?: string;
  mode: string;
  status: string;
  ebayStatus?: string;
  kleinanzeigenStatus?: string;
  nextAction: string;
}

interface ApiContextType {
  createProductCase: (mode: string, title?: string) => Promise<ProductCase>;
  listProductCases: () => Promise<ProductCase[]>;
  getProductCase: (id: string) => Promise<ProductCase>;
  updateProductCase: (id: string, data: Partial<ProductCase>) => Promise<ProductCase>;
  deleteProductCase: (id: string) => Promise<void>;
  processMedia: (id: string) => Promise<{ jobId: string; status: string }>;
  answerQuestions: (id: string, answers: Record<string, string>) => Promise<ProductCase>;
  runResearch: (id: string) => Promise<any>;
  recalculatePricing: (id: string, profile?: any) => Promise<any>;
  generateListings: (id: string) => Promise<any[]>;
  getListingDrafts: (id: string) => Promise<any[]>;
  publishListing: (draftId: string, platform: string) => Promise<any>;
  getUploadUrl: (id: string, filename: string, contentType: string) => Promise<{ uploadUrl: string; storageUrl: string; mediaAssetId: string }>;
  getDashboard: () => Promise<DashboardItem[]>;
  updateListingStatus: (listingId: string, status: string) => Promise<any>;
  setExternalUrl: (listingId: string, url: string) => Promise<any>;
  loading: boolean;
  error: string | null;
}

const ApiContext = createContext<ApiContextType | null>(null);

export function ApiProvider({ children }: { children: React.ReactNode }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const request = useCallback(async <T,>(method: string, path: string, body?: any): Promise<T> => {
    setLoading(true);
    setError(null);
    try {
      const url = `${API_BASE_URL}${path}`;
      const opts: RequestInit = {
        method,
        headers: { 'Content-Type': 'application/json' },
      };
      if (body) opts.body = JSON.stringify(body);
      const res = await fetch(url, opts);
      if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || `HTTP ${res.status}`);
      }
      if (res.status === 204) return undefined as T;
      return await res.json();
    } catch (e: any) {
      setError(e.message);
      throw e;
    } finally {
      setLoading(false);
    }
  }, []);

  const createProductCase = useCallback((mode: string, title?: string) =>
    request<ProductCase>('POST', '/api/product-cases', { sellerMode: mode, title }), [request]);

  const listProductCases = useCallback(() =>
    request<{ items: ProductCase[] }>('GET', '/api/product-cases').then(r => r.items), [request]);

  const getProductCase = useCallback((id: string) =>
    request<ProductCase>('GET', `/api/product-cases/${id}`), [request]);

  const updateProductCase = useCallback((id: string, data: Partial<ProductCase>) =>
    request<ProductCase>('PATCH', `/api/product-cases/${id}`, data), [request]);

  const deleteProductCase = useCallback((id: string) =>
    request<void>('DELETE', `/api/product-cases/${id}`), [request]);

  const processMedia = useCallback((id: string) =>
    request<{ jobId: string; status: string }>('POST', `/api/product-cases/${id}/process-media`), [request]);

  const answerQuestions = useCallback((id: string, answers: Record<string, string>) =>
    request<ProductCase>('POST', `/api/product-cases/${id}/answers`, { answers }), [request]);

  const runResearch = useCallback((id: string) =>
    request<any>('POST', `/api/product-cases/${id}/research`), [request]);

  const recalculatePricing = useCallback((id: string, profile?: any) =>
    request<any>('POST', `/api/product-cases/${id}/pricing/recalculate`, profile ? { pricingProfile: profile } : undefined), [request]);

  const generateListings = useCallback((id: string) =>
    request<any[]>('POST', `/api/product-cases/${id}/listing-drafts/generate`), [request]);

  const getListingDrafts = useCallback((id: string) =>
    request<any[]>('GET', `/api/product-cases/${id}/listing-drafts`), [request]);

  const publishListing = useCallback((draftId: string, platform: string) =>
    request<any>('POST', `/api/product-cases/listing-drafts/${draftId}/publish`, { platform }), [request]);

  const getUploadUrl = useCallback((id: string, filename: string, contentType: string) =>
    request<{ uploadUrl: string; storageUrl: string; mediaAssetId: string }>('POST', `/api/product-cases/${id}/media/upload-url`, { filename, contentType }), [request]);

  const getDashboard = useCallback(() =>
    request<{ productCases: DashboardItem[] }>('GET', '/api/dashboard').then(r => r.productCases), [request]);

  const updateListingStatus = useCallback((listingId: string, status: string) =>
    request<any>('PATCH', `/api/dashboard/listings/${listingId}/status`, { status }), [request]);

  const setExternalUrl = useCallback((listingId: string, url: string) =>
    request<any>('PATCH', `/api/dashboard/listings/${listingId}/external-url`, { externalUrl: url }), [request]);

  return (
    <ApiContext.Provider value={{
      createProductCase, listProductCases, getProductCase, updateProductCase, deleteProductCase,
      processMedia, answerQuestions, runResearch, recalculatePricing,
      generateListings, getListingDrafts, publishListing, getUploadUrl,
      getDashboard, updateListingStatus, setExternalUrl,
      loading, error
    }}>
      {children}
    </ApiContext.Provider>
  );
}

export function useApi() {
  const ctx = useContext(ApiContext);
  if (!ctx) throw new Error('useApi must be inside ApiProvider');
  return ctx;
}
