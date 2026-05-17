import React, { useEffect, useState } from 'react';
import { useApi } from '../hooks/useApi';

export default function SettingsPage() {
  const api = useApi();
  const [connections, setConnections] = useState<any[]>([]);

  useEffect(() => { loadConnections(); }, []);

  const loadConnections = async () => {
    try {
      const res = await api.request<any>('get', '/api/marketplaces/connections');
      setConnections(Array.isArray(res) ? res : []);
    } catch (e) {}
  };

  const connectEbay = async () => {
    try {
      const { url } = await api.getEbayConnectUrl();
      window.location.href = url;
    } catch (e) {}
  };

  return (
    <div className="max-w-2xl mx-auto">
      <h2 className="text-lg font-semibold text-gray-900 mb-6">Settings</h2>

      <div className="bg-white rounded-xl border border-gray-200 p-6 mb-6">
        <h3 className="font-semibold text-gray-900 mb-4">Marketplace Connections</h3>
        <div className="space-y-3">
          <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 bg-blue-600 rounded-full flex items-center justify-center text-white text-xs font-bold">eBay</div>
              <div>
                <p className="font-medium text-gray-900">eBay</p>
                {connections.find((c: any) => c.platform === 'EBAY') ? (
                  <p className="text-xs text-green-600">Connected</p>
                ) : (
                  <p className="text-xs text-gray-500">Not connected</p>
                )}
              </div>
            </div>
            <button onClick={connectEbay}
              className="bg-blue-600 hover:bg-blue-700 text-white text-sm px-4 py-2 rounded-lg font-medium transition-colors"
            >
              Connect
            </button>
          </div>

          <div className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
            <div className="flex items-center gap-3">
              <div className="w-8 h-8 bg-green-600 rounded-full flex items-center justify-center text-white text-xs font-bold">KA</div>
              <div>
                <p className="font-medium text-gray-900">Kleinanzeigen</p>
                <p className="text-xs text-gray-500">Manual tracking only</p>
              </div>
            </div>
            <span className="text-xs text-gray-400">No API available</span>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h3 className="font-semibold text-gray-900 mb-4">About</h3>
        <p className="text-sm text-gray-600">Seller Cockpit v0.1.0</p>
        <p className="text-sm text-gray-500 mt-1">AI-powered seller cockpit for physical products</p>
      </div>
    </div>
  );
}
