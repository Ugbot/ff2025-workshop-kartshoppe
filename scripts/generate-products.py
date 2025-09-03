#!/usr/bin/env python3

import json
import random

def generate_products():
    """Generate comprehensive product catalog with real inventory levels"""
    
    categories = {
        "Electronics": [
            ("UltraBook Pro 15", "High-performance laptop with Intel i9, 32GB RAM, 1TB SSD", 1899.99, ["laptop", "computer", "productivity"]),
            ("Wireless Noise-Canceling Headphones", "Premium ANC headphones with 30-hour battery", 349.99, ["audio", "wireless", "music"]),
            ("Smart Watch Pro", "Fitness tracking, GPS, heart rate monitor", 399.99, ["wearable", "fitness", "smart"]),
            ("4K Webcam", "Professional streaming camera with auto-focus", 199.99, ["camera", "streaming", "video"]),
            ("Mechanical Gaming Keyboard", "RGB backlit with Cherry MX switches", 159.99, ["gaming", "keyboard", "accessories"]),
            ("Wireless Mouse", "Ergonomic design with precision tracking", 79.99, ["mouse", "wireless", "accessories"]),
            ("27-inch 4K Monitor", "IPS panel with HDR support and USB-C", 599.99, ["monitor", "display", "4k"]),
            ("Tablet Pro 12", "12.9-inch display with stylus support", 1099.99, ["tablet", "portable", "creative"]),
            ("Bluetooth Speaker", "360-degree sound with waterproof design", 129.99, ["audio", "portable", "speaker"]),
            ("USB-C Hub", "7-in-1 multiport adapter for laptops", 49.99, ["adapter", "accessories", "usb"])
        ],
        "Fashion": [
            ("Designer Leather Jacket", "Premium Italian leather with modern cut", 599.99, ["jacket", "leather", "designer"]),
            ("Running Shoes Pro", "Advanced cushioning for marathon runners", 179.99, ["shoes", "athletic", "running"]),
            ("Luxury Handbag", "Handcrafted leather with gold hardware", 899.99, ["bag", "luxury", "accessories"]),
            ("Polarized Sunglasses", "UV protection with titanium frame", 249.99, ["sunglasses", "accessories", "summer"]),
            ("Smart Watch Band", "Premium leather replacement band", 79.99, ["accessories", "watch", "leather"]),
            ("Cashmere Sweater", "100% pure cashmere, multiple colors", 299.99, ["sweater", "luxury", "winter"]),
            ("Denim Jeans", "Premium selvedge denim, tailored fit", 149.99, ["jeans", "denim", "casual"]),
            ("Silk Scarf", "Hand-printed Italian silk", 199.99, ["scarf", "accessories", "luxury"]),
            ("Canvas Sneakers", "Classic design with comfort sole", 89.99, ["shoes", "casual", "sneakers"]),
            ("Wool Coat", "Double-breasted winter coat", 449.99, ["coat", "winter", "formal"])
        ],
        "Home & Garden": [
            ("Smart Coffee Maker", "WiFi-enabled with scheduling and grinder", 299.99, ["coffee", "smart", "kitchen"]),
            ("Air Purifier Pro", "HEPA filter for large rooms", 449.99, ["air", "health", "home"]),
            ("Smart LED Bulbs (4-pack)", "Color changing with app control", 89.99, ["lighting", "smart", "home"]),
            ("Robot Vacuum", "Self-emptying with mapping technology", 799.99, ["vacuum", "robot", "cleaning"]),
            ("High-Speed Blender", "Professional grade for smoothies", 249.99, ["blender", "kitchen", "appliances"]),
            ("Indoor Plant Collection", "Set of 5 low-maintenance plants", 149.99, ["plants", "garden", "indoor"]),
            ("Smart Thermostat", "Learning algorithm for energy savings", 229.99, ["smart", "heating", "energy"]),
            ("Ceramic Cookware Set", "Non-toxic 10-piece set", 399.99, ["cookware", "kitchen", "ceramic"]),
            ("Memory Foam Mattress", "Queen size with cooling gel", 899.99, ["mattress", "bedroom", "comfort"]),
            ("Garden Tool Set", "Professional grade stainless steel", 179.99, ["garden", "tools", "outdoor"])
        ],
        "Sports": [
            ("Premium Yoga Mat", "Extra thick with alignment guides", 89.99, ["yoga", "fitness", "mat"]),
            ("Adjustable Dumbbells", "5-50 lbs quick-change system", 599.99, ["weights", "strength", "gym"]),
            ("Carbon Fiber Bicycle", "Road bike with 21-speed Shimano", 2499.99, ["bicycle", "cycling", "outdoor"]),
            ("Tennis Racket Pro", "Graphite frame with custom grip", 299.99, ["tennis", "racket", "sports"]),
            ("Swimming Goggles", "Anti-fog with UV protection", 39.99, ["swimming", "goggles", "water"]),
            ("Fitness Tracker Band", "Heart rate, steps, sleep monitoring", 149.99, ["fitness", "tracker", "wearable"]),
            ("Golf Club Set", "Complete 14-piece titanium set", 1899.99, ["golf", "clubs", "outdoor"]),
            ("Basketball Hoop", "Adjustable height with weatherproof backboard", 499.99, ["basketball", "outdoor", "sports"]),
            ("Camping Tent", "4-person waterproof with easy setup", 349.99, ["camping", "outdoor", "tent"]),
            ("Protein Shaker Set", "3-pack with storage compartments", 29.99, ["fitness", "nutrition", "accessories"])
        ],
        "Books": [
            ("The Innovation Paradox", "Bestselling business strategy guide", 29.99, ["business", "bestseller", "strategy"]),
            ("Gourmet Cooking Masterclass", "500 recipes from world chefs", 49.99, ["cookbook", "cooking", "recipes"]),
            ("Europe Travel Guide 2024", "Complete guide to 30 countries", 39.99, ["travel", "guide", "europe"]),
            ("Quantum Reality", "Science fiction epic trilogy", 34.99, ["fiction", "sci-fi", "trilogy"]),
            ("Steve Jobs Biography", "Authorized biography by Walter Isaacson", 24.99, ["biography", "tech", "history"]),
            ("Python Programming", "Complete guide from beginner to pro", 54.99, ["programming", "tech", "education"]),
            ("Mindfulness Meditation", "Daily practices for peace", 19.99, ["self-help", "meditation", "wellness"]),
            ("World History Atlas", "Illustrated journey through time", 79.99, ["history", "atlas", "reference"]),
            ("Garden Design Bible", "Transform your outdoor space", 44.99, ["gardening", "design", "how-to"]),
            ("Children's Story Collection", "50 classic tales illustrated", 29.99, ["children", "stories", "illustrated"])
        ],
        "Toys": [
            ("LEGO Architecture Set", "Build famous landmarks", 149.99, ["lego", "building", "educational"]),
            ("Remote Control Drone", "4K camera with GPS return", 299.99, ["drone", "rc", "flying"]),
            ("Board Game Collection", "5 classic strategy games", 89.99, ["games", "board", "family"]),
            ("Science Experiment Kit", "50+ safe experiments for kids", 69.99, ["science", "educational", "stem"]),
            ("Plush Animal Set", "Soft cuddly zoo animals (6-pack)", 49.99, ["plush", "soft", "animals"]),
            ("Art Supply Mega Kit", "Complete drawing and painting set", 79.99, ["art", "creative", "supplies"]),
            ("Wooden Train Set", "100-piece track with bridges", 129.99, ["train", "wooden", "classic"]),
            ("Robot Building Kit", "Programmable with app control", 199.99, ["robot", "stem", "programming"]),
            ("Dollhouse Mansion", "3-story with furniture included", 249.99, ["dollhouse", "pretend", "furniture"]),
            ("Musical Instrument Set", "Child-safe percussion instruments", 59.99, ["music", "instruments", "educational"])
        ],
        "Beauty": [
            ("Anti-Aging Serum", "Retinol and vitamin C formula", 89.99, ["skincare", "anti-aging", "serum"]),
            ("Professional Hair Dryer", "Ionic technology with diffuser", 199.99, ["hair", "styling", "professional"]),
            ("Makeup Brush Set", "24-piece professional collection", 129.99, ["makeup", "brushes", "professional"]),
            ("Organic Face Mask Set", "5 different treatments", 49.99, ["skincare", "organic", "mask"]),
            ("Perfume Collection", "3 signature scents gift set", 149.99, ["perfume", "fragrance", "gift"]),
            ("LED Mirror", "Touch control with magnification", 179.99, ["mirror", "led", "vanity"]),
            ("Nail Care Kit", "Professional manicure/pedicure set", 69.99, ["nails", "manicure", "tools"]),
            ("Hair Straightener", "Ceramic plates with temperature control", 149.99, ["hair", "styling", "straightener"]),
            ("Body Care Gift Set", "Lotions, scrubs, and oils", 99.99, ["body", "gift", "spa"]),
            ("Beard Grooming Kit", "Complete care for facial hair", 79.99, ["beard", "grooming", "men"])
        ],
        "Food & Grocery": [
            ("Organic Coffee Beans", "Single origin Ethiopian (2 lbs)", 34.99, ["coffee", "organic", "beans"]),
            ("Artisan Chocolate Box", "24 handcrafted truffles", 49.99, ["chocolate", "gift", "artisan"]),
            ("Extra Virgin Olive Oil", "Cold-pressed Italian (1 liter)", 39.99, ["oil", "cooking", "italian"]),
            ("Spice Collection", "20 premium spices from around the world", 89.99, ["spices", "cooking", "international"]),
            ("Organic Honey Set", "3 varieties of raw honey", 44.99, ["honey", "organic", "natural"]),
            ("Gourmet Pasta Kit", "Imported Italian with sauces", 59.99, ["pasta", "italian", "gourmet"]),
            ("Tea Sampler Box", "50 premium tea bags, 10 varieties", 39.99, ["tea", "sampler", "premium"]),
            ("Protein Bar Pack", "24 bars, mixed flavors", 49.99, ["protein", "snacks", "fitness"]),
            ("Organic Snack Box", "Healthy snacks variety pack", 34.99, ["snacks", "organic", "healthy"]),
            ("Wine Selection", "3 bottles of premium red wine", 149.99, ["wine", "alcohol", "premium"])
        ]
    }
    
    products = []
    product_id = 1
    
    brands = {
        "Electronics": ["TechPro", "InnovateTech", "SmartLife", "DigitalCraft", "FutureTech"],
        "Fashion": ["StyleCraft", "UrbanTrend", "LuxeWear", "ModernFit", "ClassicStyle"],
        "Home & Garden": ["HomeEssentials", "SmartHome", "GreenLife", "ComfortZone", "EcoHome"],
        "Sports": ["SportMax", "FitPro", "ActiveLife", "PowerGear", "EndurancePro"],
        "Books": ["BookWorm", "KnowledgePress", "ReadMore", "PageTurner", "LiteraryGems"],
        "Toys": ["ToyLand", "PlayTime", "FunFactory", "KidsJoy", "CreativeMinds"],
        "Beauty": ["BeautyPlus", "GlowUp", "PureRadiance", "LuxeBeauty", "NaturalGlow"],
        "Food & Grocery": ["GourmetKitchen", "OrganicChoice", "TasteMakers", "FreshPicks", "Artisan Foods"]
    }
    
    for category, items in categories.items():
        category_brands = brands[category]
        
        for base_name, description, base_price, tags in items:
            # Generate 2-3 variants of each product
            num_variants = random.randint(2, 3)
            
            for variant in range(num_variants):
                brand = random.choice(category_brands)
                
                # Create variant name
                if variant == 0:
                    variant_name = f"{brand} {base_name}"
                    price = base_price
                elif variant == 1:
                    variant_name = f"{brand} {base_name} Pro"
                    price = base_price * 1.2
                else:
                    variant_name = f"{brand} {base_name} Premium"
                    price = base_price * 1.5
                
                # Generate realistic inventory levels
                if price < 50:
                    inventory = random.randint(50, 200)
                elif price < 200:
                    inventory = random.randint(20, 100)
                elif price < 500:
                    inventory = random.randint(10, 50)
                else:
                    inventory = random.randint(5, 25)
                
                product = {
                    "productId": f"PROD_{str(product_id).zfill(4)}",
                    "name": variant_name,
                    "description": f"{description}. Premium quality from {brand}.",
                    "category": category,
                    "brand": brand,
                    "price": round(price, 2),
                    "inventory": inventory,
                    "imageUrl": f"https://picsum.photos/400/300?random={product_id}",
                    "tags": tags + [brand.lower(), category.lower().replace(" & ", "-")],
                    "rating": round(3.5 + random.random() * 1.5, 1),
                    "reviewCount": random.randint(10, 500)
                }
                
                products.append(product)
                product_id += 1
    
    return products

def main():
    products = generate_products()
    
    # Save as single array for Flink Hybrid Source
    with open('data/initial-products.json', 'w') as f:
        json.dump(products, f, indent=2)
    
    print(f"Generated {len(products)} products across all categories")
    
    # Show category distribution
    category_counts = {}
    for product in products:
        cat = product['category']
        category_counts[cat] = category_counts.get(cat, 0) + 1
    
    print("\nCategory distribution:")
    for cat, count in sorted(category_counts.items()):
        print(f"  {cat}: {count} products")

if __name__ == "__main__":
    main()