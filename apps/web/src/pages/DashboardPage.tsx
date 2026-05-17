import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useApi } from '../hooks/useApi';

const statusColors: Record<string, string> = {
  CAPTURED: 'bg-gray-100 text-gray-800',
  PROCESSING_MEDIA: 'bg-yellow-100 text-yellow-800',
  NEEDS_USER_INFO: 'bg-orange-100 text-orange-800',
  READY_FOR_RESEARCH: 'bg-blue-100 text-blue-800',
  RESEARCHING: 'bg-cyan-100 text-cyan-800',
  PRICED: 'bg-purple-100 text-purple-800',
  LISTING_READY: 'bg-indigo-100 text-indigo-800',
  PARTIALLY_PUBLISHED: 'bg-teal-100 text-teal-800',
  PUBLISHED: 'bg-green-100 text-green-800',
  SOLD: 'bg-blue-100 text-blue-800',
  ARCHIVED: 'bg-gray-100 text-gray-500',
  FAILED: 'bg-red-100 text-red-800',
};

export default function DashboardPage() {
  const api = useApi();
  const [items, setItems] = useState<any[]>([]);

  useEffect(() => {
    api.getDashboard().then(setItems).catch(() => {});
  }, []);

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-lg font-semibold text-gray-900">Your Products</h2>
        <Link
          to="/product/new"
          className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors"
        >
          + New Product
        </Link>
      </div>

      {items.length === 0 && (
        <div className="bg-white rounded-xl border border-gray-200 p-12 text-center">
          <p className="text-gray-500 mb-4">No products yet. Start by creating your first product case.</p>
          <Link to="/product/new" className="text-blue-600 font-medium hover:underline">Create a product &rarr;</Link>
        </div>
      )}

      <div className="grid gap-4">
        {items.map((item) => (
          <Link key={item.id} to={`/product/${item.id}`} className="block">
            <div className="bg-white rounded-xl border border-gray-200 p-5 hover:shadow-md transition-shadow">
              <div className="flex items-start justify-between">
                <div>
                  <h3 className="font-semibold text-gray-900 text-lg">{item.title || 'Unnamed Product'}</h3>
                  <div className="flex items-center gap-2 mt-1">
                    <span className="text-xs text-gray-500 capitalize">{item.mode.replace(/_/g, ' ')}</span>
                    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${statusColors[item.status] || 'bg-gray-100 text-gray-800'}`}>
                      {item.status.replace(/_/g, ' ')}
                    </span>
                  </div>
                </div>
                <div className="text-sm text-blue-600 font-medium">Next: {item.nextAction}</div>
              </div>

              <div className="flex gap-3 mt-3">
                <div className={`px-2 py-1 rounded text-xs font-medium ${item.ebayStatus ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-400'}`}>
                  eBay: {item.ebayStatus || 'u2014'}
                </div>
                <div className={`px-2 py-1 rounded text-xs font-medium ${item.kleinanzeigenStatus ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-400'}`}>
                  Kleinanzeigen: {item.kleinanzeigenStatus || 'u2014'}
                </div>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
