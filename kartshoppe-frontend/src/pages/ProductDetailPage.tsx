import React, { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useCart } from '../contexts/CartContext'
import { EventTracker } from '../services/EventTracker'
import { useProductCache } from '../contexts/ProductCacheContext'

const ProductDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { addToCart } = useCart()
  const { getProduct, isLoading } = useProductCache()
  const [product, setProduct] = useState<any>(null)
  const [quantity, setQuantity] = useState(1)
  const [selectedImage, setSelectedImage] = useState(0)

  useEffect(() => {
    if (id) {
      // First try to get from cache
      const cachedProduct = getProduct(id)
      if (cachedProduct) {
        setProduct(cachedProduct)
        EventTracker.trackProductView(cachedProduct.productId, cachedProduct.name, cachedProduct.price)
      } else {
        // If not in cache, fetch from API (cache will be updated via WebSocket)
        fetchProduct()
      }
    }
  }, [id, getProduct])

  const fetchProduct = async () => {
    // Fallback if product not in cache
    // The cache will auto-update via WebSocket when product is fetched
    setProduct({
      productId: id,
      name: `Loading Product ${id}...`,
      description: `Product information is being loaded...`,
      price: 0,
      imageUrl: `https://picsum.photos/600/400?random=${id}`,
      category: 'Loading',
      rating: 0,
      reviewCount: 0,
      inventory: 0,
      tags: []
    })
  }

  const handleAddToCart = () => {
    if (product) {
      for (let i = 0; i < quantity; i++) {
        addToCart(product)
      }
      navigate('/cart')
    }
  }

  if (loading) {
    return (
      <div className="max-w-6xl mx-auto">
        <div className="card p-8 animate-pulse">
          <div className="grid grid-cols-2 gap-8">
            <div className="h-96 bg-gray-200 rounded-lg" />
            <div className="space-y-4">
              <div className="h-8 bg-gray-200 rounded w-3/4" />
              <div className="h-4 bg-gray-200 rounded" />
              <div className="h-4 bg-gray-200 rounded w-5/6" />
              <div className="h-12 bg-gray-200 rounded w-1/3" />
            </div>
          </div>
        </div>
      </div>
    )
  }

  if (!product) {
    return (
      <div className="max-w-6xl mx-auto">
        <div className="card p-12 text-center">
          <p className="text-gray-500">Product not found</p>
          <button onClick={() => navigate('/products')} className="btn-primary mt-4">
            Back to Products
          </button>
        </div>
      </div>
    )
  }

  const images = [
    product.imageUrl,
    `https://picsum.photos/600/400?random=${product.productId}_2`,
    `https://picsum.photos/600/400?random=${product.productId}_3`,
    `https://picsum.photos/600/400?random=${product.productId}_4`
  ]

  return (
    <div className="max-w-6xl mx-auto">
      <div className="card p-8">
        <div className="grid grid-cols-2 gap-8">
          {/* Image Gallery */}
          <div>
            <div className="relative h-96 rounded-lg overflow-hidden bg-gradient-to-br from-gray-100 to-gray-200 mb-4">
              <img
                src={images[selectedImage]}
                alt={product.name}
                className="w-full h-full object-cover"
              />
              {product.inventory < 10 && product.inventory > 0 && (
                <div className="absolute top-4 left-4 badge bg-yellow-100 text-yellow-800">
                  Only {product.inventory} left in stock!
                </div>
              )}
            </div>
            <div className="grid grid-cols-4 gap-2">
              {images.map((img, index) => (
                <button
                  key={index}
                  onClick={() => setSelectedImage(index)}
                  className={`relative h-20 rounded-lg overflow-hidden ${
                    selectedImage === index ? 'ring-2 ring-primary-500' : ''
                  }`}
                >
                  <img src={img} alt="" className="w-full h-full object-cover" />
                </button>
              ))}
            </div>
          </div>

          {/* Product Info */}
          <div>
            <div className="mb-4">
              <span className="badge bg-secondary-100 text-secondary-800">
                {product.category}
              </span>
              {product.tags?.map((tag: string) => (
                <span key={tag} className="ml-2 badge bg-gray-100 text-gray-700">
                  {tag}
                </span>
              ))}
            </div>

            <h1 className="text-3xl font-bold text-gray-900 mb-4">
              {product.name}
            </h1>

            <div className="flex items-center mb-4">
              <div className="flex items-center">
                {[...Array(5)].map((_, i) => (
                  <svg
                    key={i}
                    className={`w-5 h-5 ${
                      i < Math.floor(product.rating) ? 'text-yellow-400' : 'text-gray-300'
                    }`}
                    fill="currentColor"
                    viewBox="0 0 20 20"
                  >
                    <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                  </svg>
                ))}
                <span className="ml-2 text-gray-600">
                  {product.rating} ({product.reviewCount} reviews)
                </span>
              </div>
            </div>

            <p className="text-gray-600 mb-6">
              {product.description}
            </p>

            <div className="border-t border-b py-4 mb-6">
              <div className="flex items-center justify-between mb-4">
                <span className="text-3xl font-bold text-gray-900">
                  ${product.price.toFixed(2)}
                </span>
                <span className={`text-sm ${product.inventory > 0 ? 'text-green-600' : 'text-red-600'}`}>
                  {product.inventory > 0 ? '✓ In Stock' : '✗ Out of Stock'}
                </span>
              </div>

              <div className="flex items-center space-x-4">
                <div className="flex items-center">
                  <button
                    onClick={() => setQuantity(Math.max(1, quantity - 1))}
                    className="w-10 h-10 rounded-lg bg-gray-200 hover:bg-gray-300 flex items-center justify-center"
                  >
                    -
                  </button>
                  <input
                    type="number"
                    value={quantity}
                    onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                    className="w-16 h-10 text-center border-t border-b"
                  />
                  <button
                    onClick={() => setQuantity(Math.min(product.inventory, quantity + 1))}
                    className="w-10 h-10 rounded-lg bg-gray-200 hover:bg-gray-300 flex items-center justify-center"
                  >
                    +
                  </button>
                </div>

                <button
                  onClick={handleAddToCart}
                  disabled={product.inventory === 0}
                  className={`flex-1 ${
                    product.inventory === 0
                      ? 'bg-gray-300 cursor-not-allowed'
                      : 'btn-primary'
                  }`}
                >
                  {product.inventory === 0 ? 'Out of Stock' : 'Add to Cart'}
                </button>
              </div>
            </div>

            {/* Additional Info */}
            <div className="space-y-2 text-sm text-gray-600">
              <p>✓ Free shipping on orders over $50</p>
              <p>✓ 30-day return policy</p>
              <p>✓ Secure checkout</p>
              <p>✓ Real-time inventory tracking</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default ProductDetailPage