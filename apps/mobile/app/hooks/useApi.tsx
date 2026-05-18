import { useCallback } from 'react';
import { useAuth } from './useAuth';

const API_BASE = process.env.EXPO_PUBLIC_API_URL || 'https://api.sellercockpit.example.com';

export type ProductCaseStatus =
  | 'CAPTURED' | 'PROCESSING_MEDIA' | 'NEEDS_USER_INFO' | 'READY_FOR_RESEARCH'
  | 'RESEARCHING' | 'PRICED' | 'LISTING_READY' | 'PARTIALLY_PUBLISHED'
  | 'PUBLISHED' | 'SOLD' | 'ARCHIVED' | 'FAILED';

export type SellerMode = 'PRIVATE_DECLUTTERING' | 'PRIVATE_RESELLING' | 'PROFESSIONAL';

export interface ProductCase {
  id: string;
  sellerMode: SellerMode;
  status: ProductCaseStatus;
  title?: string;
  productFacts?: any;
  conditionAssessment?: any;
  pricingRecommendation?: any;
  marketResearchResult?: any;
  missingQuestions?: string[];
  listingDrafts?: any;
  createdAt: string;
  updatedAt: string;
}

export interface UploadUrlResponse {
  uploadUrl: string;
  storageUrl: string;
  mediaAssetId: string;
}

export interface ListingDraft {
  id: string;
  platform: 'EBAY' | 'KLEINANZEIGEN';
  title: string;
  description: string;
  price: { amount: string; currency: string };
  readyToPublish: boolean;
}

export function useApi() {
  const auth = useAuth();

  const request = useCallback(async <T>(
    method: string,
    path: string,
    body?: any,
    contentType?: string
  ): Promise<T> => {
    const token = await auth.getIdToken();
    const url = `${API_BASE}${path}`;
    const headers: Record<string, string> = {
      Accept: 'application/json',
    };
    if (token) headers['Authorization'] = `Bearer ${token}`;
    if (contentType) headers['Content-Type'] = contentType;
    else if (body) headers['Content-Type'] = 'application/json';

    const res = await fetch(url, {
      method,
      headers,
      body: body ? (typeof body === 'string' ? body : JSON.stringify(body)) : undefined,
    });

    if (!res.ok) {
      const text = await res.text();
      throw new Error(`HTTP ${res.status}: ${text}`);
    }
    if (res.status === 204) return undefined as T;
    return res.json() as Promise<T>;
  }, [auth]);

  return {
    // Auth
    verifyToken: () => request<any>('POST', '/api/auth/verify'),

    // Product cases
    listProductCases: () =>
      request<{ items: ProductCase[]; total: number }>('GET', '/api/product-cases').then(r => r.items),

    getProductCase: (id: string) =>
      request<ProductCase>('GET', `/api/product-cases/${id}`),

    createProductCase: (sellerMode: SellerMode, title?: string) =>
      request<ProductCase>('POST', '/api/product-cases', { sellerMode, title }),

    updateProductCase: (id: string, updates: Partial<ProductCase>) =>
      request<ProductCase>('PATCH', `/api/product-cases/${id}`, updates),

    deleteProductCase: (id: string) =>
      request<void>('DELETE', `/api/product-cases/${id}`),

    // Media
    getUploadUrl: (productCaseId: string, filename: string, contentType: string) =>
      request<UploadUrlResponse>('POST', `/api/product-cases/${productCaseId}/media/upload-url`, {
        filename,
        contentType,
      }),

    confirmUpload: (productCaseId: string, storageUrl: string) =>
      request<void>('POST', `/api/product-cases/${productCaseId}/media/complete`, { storageUrl }),

    // AI Processing
    processMedia: (id: string) =>
      request<{ jobId: string; status: string }>('POST', `/api/product-cases/${id}/process-media`),

    getJobStatus: (jobId: string) =>
      request<any>('GET', `/api/jobs/${jobId}`),

    // Facts>
    getProductFacts: (id: string) =>
      request<any>('GET', `/api/product-cases/${id}/facts`),

    updateProductFacts: (id: string, facts: any) =>
      request<any>('PATCH', `/api/product-cases/${id}/facts`, facts),

    getMissingQuestions: (id: string) =>
      request<any>('GET', `/api/product-cases/${id}/missing-questions`),

    submitAnswers: (id: string, answers: Record<string, string>) =>
      request<any>('POST', `/api/product-cases/${id}/answers`, { answers }),

    // Research
    runResearch: (id: string) =>
      request<any>('POST', `/api/product-cases/${id}/research`),

    getResearch: (id: string) =>
      request<any>('GET', `/api/product-cases/${id}/research`),

    // Pricing
    recalculatePricing: (id: string, pricingProfile?: any) =>
      request<any>('POST', `/api/product-cases/${id}/pricing/recalculate`, pricingProfile || {}),

    getPricing: (id: string) =>
      request<any>('GET', `/api/product-cases/${id}/pricing`),

    // Listings
    generateListings: (id: string) =>
      request<ListingDraft[]>('POST', `/api/product-cases/${id}/listing-drafts/generate`),

    getListings: (id: string) =>
      request<ListingDraft[]>('GET', `/api/product-cases/${id}/listing-drafts`),

    updateListingDraft: (draftId: string, updates: Partial<ListingDraft>) =>
      request<ListingDraft>('PATCH', `/api/listing-drafts/${draftId}`, updates),

    publishListing: (draftId: string) =>
      request<any>('POST', `/api/listing-drafts/${draftId}/publish`),

    // Marketplace connections
    listConnections: () =>
      request<any[]>('GET', '/api/marketplaces/connections'),

    getEbayConnectUrl: () =>
      request<{ url: string; state: string }>('GET', '/api/marketplaces/ebay/connect-url'),

    disconnectEbay: () =>
      request<any>('DELETE', '/api/marketplaces/ebay/disconnect'),

    publishEbay: (draftId: string) =>
      request<any>('POST', `/api/marketplaces/ebay/publish/${draftId}`),

    syncEbay: (listingId: string) =>
      request<any>('POST', `/api/marketplaces/ebay/sync/${listingId}`),

    endEbay: (listingId: string) =>
      request<any>('POST', `/api/marketplaces/ebay/end/${listingId}`),

    estimateEbayFees: (price: { amount: string; currency: string }, category?: string) =>
      request<any>('POST', '/api/marketplaces/ebay/fees', { price, category }),

    assistedPublish: (draftId: string) =>
      request<any>('POST', `/api/marketplaces/kleinanzeigen/assisted-publish/${draftId}`),

    // Scan coverage
    saveScanCoverage: (productCaseId: string, payload: any) =>
      request('POST', `/api/product-cases/${productCaseId}/scan-coverage`, payload),

    getScanCoverage: (productCaseId: string) =>
      request<any>('GET', `/api/product-cases/${productCaseId}/scan-coverage`),

    // Notifications
    registerPushToken: (token: string) =>
      request<void>('POST', '/api/devices/register', { fcmToken: token }),
  };
}
