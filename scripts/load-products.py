#!/usr/bin/env python3
"""
Product Loader for KartShoppe
Loads products from JSON files into Kafka for processing by Flink
"""

import json
import time
import argparse
import sys
from typing import List, Dict, Any
from kafka import KafkaProducer
from kafka.errors import KafkaError

def load_json_file(filepath: str) -> List[Dict[str, Any]]:
    """Load products from JSON file"""
    try:
        with open(filepath, 'r') as f:
            products = json.load(f)
            print(f"Loaded {len(products)} products from {filepath}")
            return products
    except FileNotFoundError:
        print(f"Error: File {filepath} not found")
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"Error: Invalid JSON in {filepath}: {e}")
        sys.exit(1)

def create_producer(bootstrap_servers: str) -> KafkaProducer:
    """Create Kafka producer"""
    try:
        producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            key_serializer=lambda k: k.encode('utf-8') if k else None,
            acks='all',
            retries=3,
            max_in_flight_requests_per_connection=1
        )
        print(f"Connected to Kafka at {bootstrap_servers}")
        return producer
    except Exception as e:
        print(f"Error connecting to Kafka: {e}")
        sys.exit(1)

def send_products(producer: KafkaProducer, products: List[Dict[str, Any]], topic: str, delay: float = 0.5):
    """Send products to Kafka topic"""
    success_count = 0
    error_count = 0
    
    for i, product in enumerate(products, 1):
        try:
            # Use productId as key for partitioning
            key = product.get('productId')
            
            # Send to Kafka
            future = producer.send(topic, key=key, value=product)
            record_metadata = future.get(timeout=10)
            
            success_count += 1
            print(f"[{i}/{len(products)}] Sent product {product['productId']}: {product['name']}")
            print(f"  → Topic: {record_metadata.topic}, Partition: {record_metadata.partition}, Offset: {record_metadata.offset}")
            
            # Delay between messages if specified
            if delay > 0 and i < len(products):
                time.sleep(delay)
                
        except KafkaError as e:
            error_count += 1
            print(f"Error sending product {product.get('productId', 'unknown')}: {e}")
        except Exception as e:
            error_count += 1
            print(f"Unexpected error: {e}")
    
    # Flush remaining messages
    producer.flush()
    
    print(f"\n✅ Successfully sent {success_count} products")
    if error_count > 0:
        print(f"❌ Failed to send {error_count} products")
    
    return success_count, error_count

def main():
    parser = argparse.ArgumentParser(description='Load products into Kafka for KartShoppe')
    parser.add_argument('--file', '-f', required=True, help='Path to JSON file containing products')
    parser.add_argument('--topic', '-t', default='product-updates', help='Kafka topic (default: product-updates)')
    parser.add_argument('--bootstrap-servers', '-b', default='localhost:19092', help='Kafka bootstrap servers (default: localhost:19092)')
    parser.add_argument('--delay', '-d', type=float, default=0.5, help='Delay between messages in seconds (default: 0.5)')
    parser.add_argument('--continuous', '-c', action='store_true', help='Continuously send products in a loop')
    parser.add_argument('--loop-delay', '-l', type=float, default=30.0, help='Delay between loops in continuous mode (default: 30)')
    
    args = parser.parse_args()
    
    # Load products from file
    products = load_json_file(args.file)
    
    if not products:
        print("No products to load")
        sys.exit(1)
    
    # Create Kafka producer
    producer = create_producer(args.bootstrap_servers)
    
    print(f"\n📦 Loading products to Kafka topic: {args.topic}")
    print(f"{'='*50}")
    
    try:
        if args.continuous:
            # Continuous mode - keep sending products in a loop
            loop_count = 0
            print(f"Running in continuous mode (press Ctrl+C to stop)")
            
            while True:
                loop_count += 1
                print(f"\n🔄 Loop {loop_count}")
                
                # Modify products slightly for each loop (simulate updates)
                modified_products = []
                for product in products:
                    p = product.copy()
                    # Randomly adjust inventory
                    import random
                    if random.random() > 0.5:
                        p['inventory'] = max(0, p['inventory'] + random.randint(-5, 10))
                    # Occasionally adjust price
                    if random.random() > 0.8:
                        p['price'] = round(p['price'] * (1 + random.uniform(-0.1, 0.1)), 2)
                    modified_products.append(p)
                
                success, errors = send_products(producer, modified_products, args.topic, args.delay)
                
                if args.loop_delay > 0:
                    print(f"⏳ Waiting {args.loop_delay} seconds before next loop...")
                    time.sleep(args.loop_delay)
        else:
            # Single run mode
            success, errors = send_products(producer, products, args.topic, args.delay)
            
    except KeyboardInterrupt:
        print("\n\n⛔ Interrupted by user")
    finally:
        producer.close()
        print("🔌 Kafka producer closed")

if __name__ == '__main__':
    main()