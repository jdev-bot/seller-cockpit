import React from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { ApiProvider } from './hooks/useApi'
import DashboardPage from './pages/DashboardPage'
import ProductCasePage from './pages/ProductCasePage'
import NewProductPage from './pages/NewProductPage'
import SettingsPage from './pages/SettingsPage'

function App() {
  return (
    <ApiProvider>
      <BrowserRouter>
        <div className="min-h-screen bg-gray-50">
          <header className="bg-white border-b border-gray-200 shadow-sm">
            <div className="max-w-7xl mx-auto px-4 py-4 flex items-center justify-between">
              <h1 className="text-xl font-bold text-gray-900">Seller Cockpit</h1>
              <nav className="flex gap-4 text-sm">
                <a href="/" className="text-gray-600 hover:text-gray-900 font-medium">Dashboard</a>
                <a href="/settings" className="text-gray-600 hover:text-gray-900 font-medium">Settings</a>
              </nav>
            </div>
          </header>
          <main className="max-w-7xl mx-auto px-4 py-6">
            <Routes>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/product/:id" element={<ProductCasePage />} />
              <Route path="/product/new" element={<NewProductPage />} />
              <Route path="/settings" element={<SettingsPage />} />
            </Routes>
          </main>
        </div>
      </BrowserRouter>
    </ApiProvider>
  )
}

export default App
