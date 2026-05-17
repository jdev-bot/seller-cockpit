import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useApi } from '../hooks/useApi';

export default function ProductCasePage() {
  const { id } = useParams<{ id: string }>();
  const api = useApi();
  const [pc, setPc] = useState<any>(null);

  const load = async () => {
    if (!id) return;
    const data = await api.getProductCase(id);
    setPc(data);
  };

  useEffect(() => { load(); }, [id]);

  if (!pc) return <div className="p-6 text-gray-500">Loading...</div>;

  return (
    <div className="max-w-4xl mx-auto">
      <Link to="/" className="text-sm text-gray-500 hover:text-gray-900 mb-4 block">u2190 Back to dashboard</Link>

      <div className="bg-white rounded-xl border border-gray-200 p-6 mb-6">
        <h1 className="text-xl font-bold text-gray-900">{pc.title || pc.productFacts?.title || 'Product Case'}</h1>
        <div className="flex items-center gap-2 mt-2">
          <span className="text-sm text-gray-500">{pc.sellerMode.replace(/_/g, ' ')}</span>
          <span className="text-sm text-gray-300">u00b7</span>
          <span className="text-sm text-blue-600 font-medium">{pc.status.replace(/_/g, ' ')}</span>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <ActionCard title="Product Facts" status={pc.productFacts ? 'done' : 'open'} onClick={() => {}} >
          {pc.productFacts ? (
            <div className="text-sm text-gray-600 space-y-1">
              <p><span className="font-medium">Title:</span> {pc.productFacts.title}</p>
              {pc.productFacts.brand && <p><span className="font-medium">Brand:</span> {pc.productFacts.brand}</p>}
              {pc.productFacts.model && <p><span className="font-medium">Model:</span> {pc.productFacts.model}</p>}
              <p><span className="font-medium">Confidence:</span> {(pc.productFacts.confidence * 100).toFixed(0)}%</p>
              {pc.missingQuestions.length > 0 && (
                <div className="mt-2 p-2 bg-yellow-50 border border-yellow-200 rounded text-xs text-yellow-800">
                  <p className="font-medium">Missing info:</p>
                  {pc.missingQuestions.map((q: string, i: number) => <p key={i}>u2022 {q}</p>)}
                </div>
              )}
            </div>
          ) : (
            <p className="text-sm text-gray-500">No product facts yet. Upload media and run AI processing.</p>
          )}
        </ActionCard>

        <ActionCard title="Market Research" status={pc.marketResearchResult ? 'done' : 'open'} onClick={handleResearch} >
          {pc.marketResearchResult ? (
            <div className="text-sm text-gray-600">
              <p className="font-medium text-gray-900">
                {pc.marketResearchResult.estimatedMarketMid?.amount?.toFixed?.(0)} u20ac
                <span className="text-gray-400 font-normal"> u2013 mid</span>
              </p>
              <p>Low: {pc.marketResearchResult.estimatedMarketLow?.amount?.toFixed?.(0)} u20ac u00b7 High: {pc.marketResearchResult.estimatedMarketHigh?.amount?.toFixed?.(0)} u20ac</p>
              <p className="text-xs text-gray-400 mt-1">Confidence: {pc.marketResearchResult.confidence}</p>
            </div>
          ) : (
            <p className="text-sm text-gray-500">Run market research to get price estimates.</p>
          )}
        </ActionCard>

        <ActionCard title="Pricing" status={pc.pricingRecommendation ? 'done' : 'open'} onClick={handlePricing} >
          {pc.pricingRecommendation ? (
            <div className="text-sm text-gray-600 space-y-1">
              <p><span className="font-medium">Recommended:</span> {pc.pricingRecommendation.recommendedPrice?.amount?.toFixed?.(2)} u20ac</p>
              {pc.pricingRecommendation.quickSalePrice && <p><span className="font-medium">Quick sale:</span> {pc.pricingRecommendation.quickSalePrice.amount.toFixed(2)} u20ac</p>}
              {pc.pricingRecommendation.optimisticPrice && <p><span className="font-medium">Optimistic:</span> {pc.pricingRecommendation.optimisticPrice.amount.toFixed(2)} u20ac</p>}
              {pc.pricingRecommendation.expectedPayout && <p><span className="font-medium text-green-600">Expected payout:</span> {pc.pricingRecommendation.expectedPayout.amount.toFixed(2)} u20ac</p>}
              {pc.pricingRecommendation.estimatedProfit && <p><span className="font-medium text-green-600">Profit:</span> {pc.pricingRecommendation.estimatedProfit.amount.toFixed(2)} u20ac ({pc.pricingRecommendation.roiPercent}%)</p>}
            </div>
          ) : (
            <p className="text-sm text-gray-500">Calculate pricing after market research is complete.</p>
          )}
        </ActionCard>

        <ActionCard title="Listings" status={pc.status === 'LISTING_READY' || pc.status === 'PUBLISHED' || pc.status === 'PARTIALLY_PUBLISHED' ? 'done' : 'open'} onClick={handleGenerateListings} >
          <p className="text-sm text-gray-500">Generate platform-optimized listing drafts for eBay and Kleinanzeigen.</p>
        </ActionCard>
      </div>
    </div>
  );

  async function handleResearch() {
    if (!id) return;
    await api.runResearch(id);
    await load();
  }

  async function handlePricing() {
    if (!id) return;
    await api.recalculatePricing(id);
    await load();
  }

  async function handleGenerateListings() {
    if (!id) return;
    await api.generateListings(id);
    await load();
  }
}

function ActionCard({ title, status, children, onClick }: { title: string; status: 'done' | 'open'; children: React.ReactNode; onClick?: () => void }) {
  return (
    <div className={`bg-white rounded-xl border ${status === 'done' ? 'border-green-200' : 'border-gray-200'} p-5`}>
      <div className="flex items-center justify-between mb-3">
        <h3 className="font-semibold text-gray-900">{title}</h3>
        <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
          status === 'done' ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'
        }`}>
          {status === 'done' ? 'Done' : 'Open'}
        </span>
      </div>
      <div className="mb-4">{children}</div>
      {onClick && (
        <button
          onClick={onClick}
          className="text-sm bg-blue-600 hover:bg-blue-700 text-white px-3 py-1.5 rounded-lg font-medium transition-colors"
        >
          {status === 'done' ? 'Refresh' : 'Run'}
        </button>
      )}
    </div>
  );
}
