import React, { createContext, useContext, useState, useCallback } from 'react';
import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const api = axios.create({ baseURL: API_BASE_URL });

export interface ProductCase {
  id: string; sellerMode: string; status: string; title?: string;
  productFacts?: any; conditionAssessment?: any;
  pricingRecommendation?: any; marketResearchResult?: any;
  missingQuestions: string[]; complianceWarnings: string[];
}

interface ApiContextType {
  getDashboard: () => Promise<any[]>;
  getProductCase: (id: string) => Promise<ProductCase>;
  createProductCase: (mode: string, title?: string) => Promise<ProductCase>;
  listProductCases: () => Promise<ProductCase[]>;
  updateProductCase: (id: string, data: any) => Promise<ProductCase>;
  deleteProductCase: (id: string) => Promise<void>;
  processMedia: (id: string) => Promise<any>;
  runResearch: (id: string) => Promise<any>;
  recalculatePricing: (id: string, profile?: any) => Promise<any>;
  generateListings: (id: string) => Promise<any[]>;
  publishListing: (draftId: string, platform: string) => Promise<any>;
  getEbayConnectUrl: () => Promise<{ url: string }>;
  request: <T>(method: 'get'|'post'|'patch'|'delete', path: string, data?: any) => Promise<T>;
  loading: boolean; error: string | null;
}

const ApiContext = createContext<ApiContextType | null>(null);

export function ApiProvider({ children }: { children: React.ReactNode }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const request = useCallback(async <T,>(method: 'get'|'post'|'patch'|'delete', path: string, data?: any): Promise<T> => {
    setLoading(true); setError(null);
    try {
      const res = await api.request({ method, url: path, data });
      return res.data;
    } catch (e: any) { setError(e.message); throw e; }
    finally { setLoading(false); }
  }, []);

  const val: ApiContextType = {
    getDashboard: async () => (await request<{ productCases: any[] }>('get', '/api/dashboard')).productCases,
    getProductCase: (id) => request<ProductCase>('get', '/api/product-cases/' + id),
    createProductCase: (mode, title) => request<ProductCase>('post', '/api/product-cases', { sellerMode: mode, title }),
    listProductCases: async () => (await request<{ items: ProductCase[] }>('get', '/api/product-cases')).items,
    updateProductCase: (id, data) => request<ProductCase>('patch', '/api/product-cases/' + id, data),
    deleteProductCase: (id) => request<void>('delete', '/api/product-cases/' + id),
    processMedia: (id) => request<any>('post', '/api/product-cases/' + id + '/process-media'),
    runResearch: (id) => request<any>('post', '/api/product-cases/' + id + '/research'),
    recalculatePricing: (id, profile) => request<any>('post', '/api/product-cases/' + id + '/pricing/recalculate', profile ? { pricingProfile: profile } : undefined),
    generateListings: (id) => request<any[]>('post', '/api/product-cases/' + id + '/listing-drafts/generate'),
    publishListing: (draftId, platform) => request<any>('post', '/api/product-cases/listing-drafts/' + draftId + '/publish', { platform }),
    getEbayConnectUrl: () => request<{ url: string }>('get', '/api/marketplaces/ebay/connect-url'),
    request,
    loading, error
  };

  return React.createElement(ApiContext.Provider, { value: val }, children);
}

export function useApi() {
  const ctx = useContext(ApiContext);
  if (!ctx) throw new Error('useApi must be inside ApiProvider');
  return ctx;
}
