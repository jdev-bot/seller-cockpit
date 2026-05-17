import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApi } from '../hooks/useApi';

const MODES = [
  { key: 'PRIVATE_DECLUTTERING', label: 'Private Decluttering', desc: 'I own this item and just want to sell it.' },
  { key: 'PRIVATE_RESELLING', label: 'Private Reselling', desc: 'I bought this item to resell or want to consider purchase price.' },
  { key: 'PROFESSIONAL', label: 'Professional Selling', desc: 'I sell commercially and need tax/cost/margin calculation.' },
];

export default function NewProductPage() {
  const api = useApi();
  const navigate = useNavigate();
  const [mode, setMode] = useState('PRIVATE_DECLUTTERING');
  const [title, setTitle] = useState('');

  const onSubmit = async () => {
    const pc = await api.createProductCase(mode, title || undefined);
    navigate('/product/' + pc.id);
  };

  return (
    <div className="max-w-xl mx-auto">
      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-1">New Product</h2>
        <p className="text-sm text-gray-500 mb-6">Step 1 of 2 &mdash; select seller mode</p>

        <div className="space-y-3 mb-6">
          {MODES.map((m) => (
            <button
              key={m.key}
              onClick={() => setMode(m.key)}
              className={`w-full text-left p-4 rounded-lg border-2 transition-all ${
                mode === m.key
                  ? 'border-blue-500 bg-blue-50'
                  : 'border-gray-200 hover:border-gray-300'
              }`}
            >
              <div className="flex items-center gap-3">
                <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center ${
                  mode === m.key ? 'border-blue-500' : 'border-gray-300'
                }`}>
                  {mode === m.key && <div className="w-2.5 h-2.5 rounded-full bg-blue-500" />}
                </div>
                <div>
                  <p className="font-semibold text-gray-900">{m.label}</p>
                  <p className="text-sm text-gray-500">{m.desc}</p>
                </div>
              </div>
            </button>
          ))}
        </div>

        <label className="block text-sm font-medium text-gray-700 mb-2">Product name (optional)</label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="e.g., Apple Magic Keyboard"
          className="w-full border border-gray-300 rounded-lg px-3 py-2 mb-6 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
        />

        <button
          onClick={onSubmit}
          className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-3 rounded-lg transition-colors"
        >
          Continue
        </button>
      </div>
    </div>
  );
}
