import React, { createContext, useContext, useState, useEffect, useCallback } from 'react'

interface Product {
  productId: string
  name: string
  description: string
  price: number
  category: string
  imageUrl: string
  inventory: number
  tags: string[]
  rating: number
  reviewCount: number
}

interface ProductCacheContextType {
  products: Product[]
  isLoading: boolean
  error: string | null
  lastFetched: number | null
  refreshProducts: () => Promise<void>
  clearCache: () => void
  getProduct: (id: string) => Product | undefined
  getProductsByCategory: (category: string) => Product[]
  getFeaturedProducts: () => Product[]
}

const ProductCacheContext = createContext<ProductCacheContextType | undefined>(undefined)

const CACHE_KEY = 'kartshoppe_product_cache'
const CACHE_DURATION = 5 * 60 * 1000 // 5 minutes

export const ProductCacheProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [products, setProducts] = useState<Product[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastFetched, setLastFetched] = useState<number | null>(null)

  // Load cache from localStorage on mount
  useEffect(() => {
    const loadCache = () => {
      try {
        const cached = localStorage.getItem(CACHE_KEY)
        if (cached) {
          const { products: cachedProducts, timestamp } = JSON.parse(cached)
          const now = Date.now()
          
          // Check if cache is still valid
          if (now - timestamp < CACHE_DURATION) {
            setProducts(cachedProducts)
            setLastFetched(timestamp)
          } else {
            // Cache expired, fetch new data
            refreshProducts()
          }
        } else {
          // No cache, fetch data
          refreshProducts()
        }
      } catch (err) {
        console.error('Failed to load product cache:', err)
        refreshProducts()
      }
    }

    loadCache()
  }, [])

  // Save to localStorage whenever products change
  useEffect(() => {
    if (products.length > 0 && lastFetched) {
      try {
        localStorage.setItem(CACHE_KEY, JSON.stringify({
          products,
          timestamp: lastFetched
        }))
      } catch (err) {
        console.error('Failed to save product cache:', err)
      }
    }
  }, [products, lastFetched])

  const refreshProducts = useCallback(async () => {
    setIsLoading(true)
    setError(null)

    try {
      // Fetch full inventory state from the new endpoint
      const response = await fetch('/api/ecommerce/inventory/state')
      if (!response.ok) {
        throw new Error(`Failed to fetch inventory state: ${response.statusText}`)
      }

      const state = await response.json()
      setProducts(state.products || [])
      setLastFetched(Date.now())
      
      console.log(`Hydrated cache with ${state.totalProducts} products from ${state.categories?.length || 0} categories`)
    } catch (err) {
      // Fallback to regular products endpoint
      try {
        const response = await fetch('/api/ecommerce/products')
        if (!response.ok) {
          throw new Error(`Failed to fetch products: ${response.statusText}`)
        }
        const data = await response.json()
        setProducts(data)
        setLastFetched(Date.now())
      } catch (fallbackErr) {
        setError(err instanceof Error ? err.message : 'Failed to fetch products')
        console.error('Error fetching products:', err)
        
        // Try to use stale cache if available
        const cached = localStorage.getItem(CACHE_KEY)
        if (cached) {
          try {
            const { products: cachedProducts } = JSON.parse(cached)
            setProducts(cachedProducts)
            console.log('Using stale cache with', cachedProducts.length, 'products')
          } catch (cacheErr) {
            console.error('Failed to load stale cache:', cacheErr)
          }
        }
      }
    } finally {
      setIsLoading(false)
    }
  }, [])

  const clearCache = useCallback(() => {
    localStorage.removeItem(CACHE_KEY)
    setProducts([])
    setLastFetched(null)
    setError(null)
  }, [])

  const getProduct = useCallback((id: string) => {
    return products.find(p => p.productId === id)
  }, [products])

  const getProductsByCategory = useCallback((category: string) => {
    if (category === 'All') return products
    return products.filter(p => p.category === category)
  }, [products])

  const getFeaturedProducts = useCallback(() => {
    // Return top-rated products or first 8 products
    return [...products]
      .sort((a, b) => (b.rating * b.reviewCount) - (a.rating * a.reviewCount))
      .slice(0, 8)
  }, [products])

  // Set up periodic refresh
  useEffect(() => {
    const interval = setInterval(() => {
      const now = Date.now()
      if (lastFetched && now - lastFetched > CACHE_DURATION) {
        refreshProducts()
      }
    }, 60000) // Check every minute

    return () => clearInterval(interval)
  }, [lastFetched, refreshProducts])

  // Listen for WebSocket updates
  useEffect(() => {
    const handleWebSocketUpdate = (event: CustomEvent) => {
      const { type, data } = event.detail
      
      if (type === 'inventory_update' && data.currentProduct) {
        setProducts(prev => {
          const index = prev.findIndex(p => p.productId === data.currentProduct.productId)
          if (index >= 0) {
            // Update existing product with new inventory data
            const updated = [...prev]
            updated[index] = {
              ...updated[index],
              ...data.currentProduct,
              inventory: data.currentInventory || data.currentProduct.inventory
            }
            console.log(`Updated product ${data.currentProduct.productId} via WebSocket`)
            return updated
          } else {
            // Add new product from WebSocket
            console.log(`Added new product ${data.currentProduct.productId} via WebSocket`)
            return [...prev, data.currentProduct]
          }
        })
        
        // Update localStorage with the new data
        setLastFetched(Date.now())
      }
    }

    window.addEventListener('websocket:message', handleWebSocketUpdate as EventListener)
    return () => {
      window.removeEventListener('websocket:message', handleWebSocketUpdate as EventListener)
    }
  }, [])

  const value: ProductCacheContextType = {
    products,
    isLoading,
    error,
    lastFetched,
    refreshProducts,
    clearCache,
    getProduct,
    getProductsByCategory,
    getFeaturedProducts
  }

  return (
    <ProductCacheContext.Provider value={value}>
      {children}
    </ProductCacheContext.Provider>
  )
}

export const useProductCache = () => {
  const context = useContext(ProductCacheContext)
  if (!context) {
    throw new Error('useProductCache must be used within a ProductCacheProvider')
  }
  return context
}