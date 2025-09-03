import React, { createContext, useContext, useState, useEffect } from 'react'
import { useWebSocket } from './WebSocketContext'
import { EventTracker } from '../services/EventTracker'

interface CartItem {
  productId: string
  productName: string
  price: number
  quantity: number
  imageUrl: string
}

interface CartContextType {
  items: CartItem[]
  totalAmount: number
  addToCart: (product: any) => void
  removeFromCart: (productId: string) => void
  updateQuantity: (productId: string, quantity: number) => void
  clearCart: () => void
  itemCount: number
}

const CartContext = createContext<CartContextType | undefined>(undefined)

export const CartProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [items, setItems] = useState<CartItem[]>([])
  const { sendMessage } = useWebSocket()

  useEffect(() => {
    const savedCart = localStorage.getItem('cart')
    if (savedCart) {
      setItems(JSON.parse(savedCart))
    }
  }, [])

  useEffect(() => {
    localStorage.setItem('cart', JSON.stringify(items))
  }, [items])

  const addToCart = (product: any) => {
    const existingItem = items.find(item => item.productId === product.productId)
    
    if (existingItem) {
      updateQuantity(product.productId, existingItem.quantity + 1)
    } else {
      const newItem: CartItem = {
        productId: product.productId,
        productName: product.name,
        price: product.price,
        quantity: 1,
        imageUrl: product.imageUrl
      }
      setItems([...items, newItem])
      
      EventTracker.trackEvent('ADD_TO_CART', {
        productId: product.productId,
        value: product.price,
        quantity: 1
      })
      
      sendMessage({
        type: 'CART_UPDATE',
        action: 'ADD',
        payload: newItem
      })
    }
  }

  const removeFromCart = (productId: string) => {
    setItems(items.filter(item => item.productId !== productId))
    
    EventTracker.trackEvent('REMOVE_FROM_CART', {
      productId
    })
    
    sendMessage({
      type: 'CART_UPDATE',
      action: 'REMOVE',
      payload: { productId }
    })
  }

  const updateQuantity = (productId: string, quantity: number) => {
    if (quantity <= 0) {
      removeFromCart(productId)
      return
    }
    
    setItems(items.map(item =>
      item.productId === productId
        ? { ...item, quantity }
        : item
    ))
    
    EventTracker.trackEvent('UPDATE_CART_QUANTITY', {
      productId,
      quantity
    })
    
    sendMessage({
      type: 'CART_UPDATE',
      action: 'UPDATE_QUANTITY',
      payload: { productId, quantity }
    })
  }

  const clearCart = () => {
    setItems([])
    sendMessage({
      type: 'CART_UPDATE',
      action: 'CLEAR',
      payload: {}
    })
  }

  const totalAmount = items.reduce((sum, item) => sum + (item.price * item.quantity), 0)
  const itemCount = items.reduce((sum, item) => sum + item.quantity, 0)

  return (
    <CartContext.Provider value={{
      items,
      totalAmount,
      addToCart,
      removeFromCart,
      updateQuantity,
      clearCart,
      itemCount
    }}>
      {children}
    </CartContext.Provider>
  )
}

export const useCart = () => {
  const context = useContext(CartContext)
  if (context === undefined) {
    throw new Error('useCart must be used within a CartProvider')
  }
  return context
}