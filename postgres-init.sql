-- ============================================
-- E-Commerce Database Initialization for CDC
-- ============================================
-- This script sets up a sample e-commerce database
-- with tables optimized for Flink CDC to Paimon

\c ecommerce;

-- ============================================
-- 1. PRODUCTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS products (
    product_id VARCHAR(50) PRIMARY KEY,
    product_name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    subcategory VARCHAR(100),
    brand VARCHAR(100),
    price DECIMAL(10, 2) NOT NULL,
    cost DECIMAL(10, 2),
    description TEXT,
    image_url VARCHAR(500),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 2. CUSTOMERS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS customers (
    customer_id VARCHAR(50) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(20),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(50),
    postal_code VARCHAR(20),
    country VARCHAR(50) DEFAULT 'US',
    registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    total_orders INTEGER DEFAULT 0,
    lifetime_value DECIMAL(12, 2) DEFAULT 0.00
);

-- ============================================
-- 3. ORDERS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(50) PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'pending',
    subtotal DECIMAL(10, 2) NOT NULL,
    tax DECIMAL(10, 2) DEFAULT 0.00,
    shipping DECIMAL(10, 2) DEFAULT 0.00,
    total DECIMAL(10, 2) NOT NULL,
    payment_method VARCHAR(50),
    shipping_address TEXT,
    tracking_number VARCHAR(100),
    notes TEXT,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

-- ============================================
-- 4. ORDER_ITEMS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS order_items (
    order_item_id SERIAL PRIMARY KEY,
    order_id VARCHAR(50) NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price DECIMAL(10, 2) NOT NULL,
    discount DECIMAL(10, 2) DEFAULT 0.00,
    line_total DECIMAL(10, 2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- ============================================
-- 5. INVENTORY TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS inventory (
    inventory_id SERIAL PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL UNIQUE,
    warehouse_location VARCHAR(100) DEFAULT 'MAIN',
    quantity_on_hand INTEGER DEFAULT 0,
    quantity_reserved INTEGER DEFAULT 0,
    quantity_available INTEGER GENERATED ALWAYS AS (quantity_on_hand - quantity_reserved) STORED,
    reorder_point INTEGER DEFAULT 10,
    reorder_quantity INTEGER DEFAULT 50,
    last_restock_date TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- ============================================
-- 6. PRODUCT_VIEWS TABLE (for analytics)
-- ============================================
CREATE TABLE IF NOT EXISTS product_views (
    view_id SERIAL PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL,
    customer_id VARCHAR(50),
    session_id VARCHAR(100),
    view_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(50),
    device_type VARCHAR(50),
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

-- ============================================
-- SAMPLE DATA: PRODUCTS
-- ============================================

-- Electronics
INSERT INTO products (product_id, product_name, category, subcategory, brand, price, cost, description) VALUES
('prod_laptop_001', 'Gaming Laptop Pro', 'electronics', 'computers', 'TechBrand', 1299.99, 850.00, '15.6" high-performance gaming laptop'),
('prod_laptop_002', 'Business Ultrabook', 'electronics', 'computers', 'ProTech', 999.99, 650.00, 'Lightweight 13" ultrabook for professionals'),
('prod_mouse_001', 'Wireless Gaming Mouse', 'electronics', 'peripherals', 'TechBrand', 59.99, 25.00, 'RGB wireless gaming mouse'),
('prod_keyboard_001', 'Mechanical Keyboard', 'electronics', 'peripherals', 'TechBrand', 89.99, 40.00, 'RGB mechanical gaming keyboard'),
('prod_monitor_001', '27" 4K Monitor', 'electronics', 'displays', 'ViewMax', 399.99, 250.00, '27-inch 4K UHD monitor'),
('prod_headset_001', 'Wireless Headset', 'electronics', 'audio', 'SoundPro', 149.99, 75.00, 'Noise-cancelling wireless headset'),
('prod_phone_001', 'Smartphone Pro', 'electronics', 'mobile', 'MobileTech', 899.99, 550.00, '6.5" flagship smartphone'),
('prod_charger_001', 'Fast Charger 65W', 'electronics', 'accessories', 'PowerUp', 29.99, 12.00, 'USB-C fast charger'),
('prod_case_001', 'Phone Case Premium', 'electronics', 'accessories', 'GuardTech', 24.99, 8.00, 'Protective phone case'),
('prod_camera_001', 'DSLR Camera Pro', 'electronics', 'photography', 'PhotoMax', 1499.99, 950.00, 'Professional DSLR camera'),
('prod_sd_card_001', 'SD Card 128GB', 'electronics', 'storage', 'DataStore', 34.99, 15.00, 'High-speed SD card'),
('prod_tripod_001', 'Camera Tripod', 'electronics', 'photography', 'StablePro', 79.99, 35.00, 'Adjustable camera tripod');

-- Fashion
INSERT INTO products (product_id, product_name, category, subcategory, brand, price, cost, description) VALUES
('prod_shirt_001', 'Casual Cotton Shirt', 'fashion', 'tops', 'StyleCo', 39.99, 18.00, 'Comfortable cotton casual shirt'),
('prod_pants_001', 'Chino Pants', 'fashion', 'bottoms', 'StyleCo', 49.99, 22.00, 'Classic fit chino pants'),
('prod_dress_001', 'Summer Dress', 'fashion', 'dresses', 'Elegance', 69.99, 30.00, 'Floral summer dress'),
('prod_shoes_001', 'Running Shoes', 'fashion', 'footwear', 'ActiveGear', 89.99, 40.00, 'Lightweight running shoes'),
('prod_belt_001', 'Leather Belt', 'fashion', 'accessories', 'ClassicWear', 29.99, 12.00, 'Genuine leather belt'),
('prod_jeans_001', 'Slim Fit Jeans', 'fashion', 'bottoms', 'DenimPro', 59.99, 28.00, 'Modern slim fit jeans'),
('prod_jacket_001', 'Winter Jacket', 'fashion', 'outerwear', 'WarmStyle', 129.99, 65.00, 'Insulated winter jacket'),
('prod_scarf_001', 'Wool Scarf', 'fashion', 'accessories', 'CozyWear', 24.99, 10.00, 'Soft wool scarf');

-- Home & Kitchen
INSERT INTO products (product_id, product_name, category, subcategory, brand, price, cost, description) VALUES
('prod_coffee_maker_001', 'Coffee Maker Deluxe', 'home_kitchen', 'appliances', 'BrewMaster', 89.99, 45.00, 'Programmable coffee maker'),
('prod_coffee_beans_001', 'Premium Coffee Beans 1lb', 'home_kitchen', 'groceries', 'RoastCo', 14.99, 6.00, 'Arabica coffee beans'),
('prod_filters_001', 'Coffee Filters 200ct', 'home_kitchen', 'supplies', 'BrewMaster', 5.99, 2.00, 'Paper coffee filters'),
('prod_blender_001', 'High-Speed Blender', 'home_kitchen', 'appliances', 'BlendPro', 129.99, 60.00, 'Professional blender'),
('prod_pan_001', 'Non-Stick Frying Pan', 'home_kitchen', 'cookware', 'ChefLine', 39.99, 18.00, '12-inch frying pan'),
('prod_spatula_001', 'Silicone Spatula Set', 'home_kitchen', 'utensils', 'ChefLine', 19.99, 8.00, 'Heat-resistant spatula set'),
('prod_knife_set_001', 'Kitchen Knife Set', 'home_kitchen', 'cutlery', 'SharpEdge', 79.99, 35.00, '5-piece knife set'),
('prod_cutting_board_001', 'Bamboo Cutting Board', 'home_kitchen', 'prep', 'EcoKitchen', 29.99, 12.00, 'Large bamboo board');

-- Sports & Outdoors
INSERT INTO products (product_id, product_name, category, subcategory, brand, price, cost, description) VALUES
('prod_yoga_mat_001', 'Premium Yoga Mat', 'sports', 'fitness', 'FlexFit', 39.99, 18.00, 'Extra-thick yoga mat'),
('prod_yoga_blocks_001', 'Yoga Block Set', 'sports', 'fitness', 'FlexFit', 19.99, 8.00, 'Foam yoga blocks (2-pack)'),
('prod_water_bottle_001', 'Insulated Water Bottle', 'sports', 'hydration', 'HydroMax', 24.99, 10.00, '32oz insulated bottle'),
('prod_bicycle_001', 'Mountain Bike Pro', 'sports', 'cycling', 'RideTech', 599.99, 350.00, '27.5" mountain bike'),
('prod_helmet_001', 'Bike Helmet', 'sports', 'cycling', 'SafeRide', 49.99, 22.00, 'Adjustable bike helmet'),
('prod_dumbbells_001', 'Dumbbell Set 20lb', 'sports', 'strength', 'IronFit', 79.99, 40.00, 'Adjustable dumbbells'),
('prod_tennis_racket_001', 'Tennis Racket Pro', 'sports', 'racquet', 'CourtKing', 129.99, 65.00, 'Professional tennis racket'),
('prod_tennis_balls_001', 'Tennis Balls 12-pack', 'sports', 'racquet', 'CourtKing', 19.99, 8.00, 'High-visibility tennis balls');

-- ============================================
-- SAMPLE DATA: CUSTOMERS
-- ============================================
INSERT INTO customers (customer_id, email, first_name, last_name, phone, city, state, total_orders, lifetime_value) VALUES
('cust_001', 'john.doe@email.com', 'John', 'Doe', '555-0101', 'San Francisco', 'CA', 5, 2145.50),
('cust_002', 'jane.smith@email.com', 'Jane', 'Smith', '555-0102', 'New York', 'NY', 3, 1234.99),
('cust_003', 'bob.johnson@email.com', 'Bob', 'Johnson', '555-0103', 'Seattle', 'WA', 8, 3456.78),
('cust_004', 'alice.williams@email.com', 'Alice', 'Williams', '555-0104', 'Austin', 'TX', 2, 456.99),
('cust_005', 'charlie.brown@email.com', 'Charlie', 'Brown', '555-0105', 'Portland', 'OR', 12, 5678.90);

-- ============================================
-- SAMPLE DATA: INVENTORY
-- ============================================
INSERT INTO inventory (product_id, quantity_on_hand, quantity_reserved, reorder_point, reorder_quantity)
SELECT product_id, 100, 0, 20, 50 FROM products;

-- ============================================
-- PUBLICATION FOR CDC
-- ============================================
-- Create a publication for all tables (Flink CDC will subscribe to this)
CREATE PUBLICATION paimon_cdc FOR ALL TABLES;

-- ============================================
-- INDEXES FOR PERFORMANCE
-- ============================================
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_price ON products(price);
CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_date ON orders(order_date);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);
CREATE INDEX idx_inventory_product ON inventory(product_id);

-- ============================================
-- TRIGGER: UPDATE timestamp
-- ============================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_inventory_updated_at BEFORE UPDATE ON inventory
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================
-- SUMMARY
-- ============================================
SELECT
    'Database Initialized Successfully' as status,
    COUNT(*) as total_products
FROM products;

SELECT
    'Sample Customers Created' as status,
    COUNT(*) as total_customers
FROM customers;

\echo '========================================='
\echo 'E-Commerce Database Ready for CDC!'
\echo 'Publication created: paimon_cdc'
\echo 'Tables: products, customers, orders, order_items, inventory, product_views'
\echo 'WAL Level: logical (CDC-ready)'
\echo '========================================='
