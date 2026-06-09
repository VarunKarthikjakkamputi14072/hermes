#!/usr/bin/env python3
"""Load the real Olist catalogue into Hermes' products table.

The Brazilian E-Commerce Public Dataset by Olist (Kaggle) ships ~33k products
across ~100k orders. This script derives a sellable catalogue from it:

  - one row per product_id in olist_products_dataset.csv
  - price taken from the median sale price in olist_order_items_dataset.csv
  - stock seeded from observed demand so the system runs hot but not impossible

Usage:
  pip install psycopg2-binary pandas
  python scripts/load_olist.py --data ./data --dsn postgresql://hermes:hermes@localhost:5432/hermes

Download the CSVs from:
  https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce
and drop olist_products_dataset.csv + olist_order_items_dataset.csv into ./data
"""
import argparse
import csv
import os
import random
import statistics
from collections import defaultdict


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default="./data", help="folder with the Olist CSVs")
    parser.add_argument("--dsn", default=os.environ.get(
        "DB_DSN", "postgresql://hermes:hermes@localhost:5432/hermes"))
    parser.add_argument("--limit", type=int, default=0, help="cap number of products (0 = all)")
    args = parser.parse_args()

    items_path = os.path.join(args.data, "olist_order_items_dataset.csv")
    products_path = os.path.join(args.data, "olist_products_dataset.csv")

    # price + demand per product
    prices = defaultdict(list)
    demand = defaultdict(int)
    with open(items_path, newline="") as f:
        for row in csv.DictReader(f):
            pid = row["product_id"]
            try:
                prices[pid].append(float(row["price"]))
            except (KeyError, ValueError):
                pass
            demand[pid] += 1

    rows = []
    with open(products_path, newline="") as f:
        for row in csv.DictReader(f):
            pid = row["product_id"]
            if pid not in prices:
                continue
            price = round(statistics.median(prices[pid]), 2)
            category = row.get("product_category_name") or "uncategorised"
            # seed stock at ~1.5x historical demand so some orders still reject
            stock = max(1, int(demand[pid] * 1.5) + random.randint(0, 20))
            rows.append((pid[:64], f"{category} {pid[:8]}", category, price, stock))
            if args.limit and len(rows) >= args.limit:
                break

    print(f"Prepared {len(rows)} products. Inserting…")

    import psycopg2  # imported late so --help works without the dep
    conn = psycopg2.connect(args.dsn)
    with conn, conn.cursor() as cur:
        cur.execute("DELETE FROM orders")
        cur.execute("DELETE FROM products")
        cur.executemany(
            "INSERT INTO products (sku, name, category, price, stock_available, version) "
            "VALUES (%s, %s, %s, %s, %s, 0)",
            rows,
        )
    conn.close()
    print("Done. Update loadtest SKU_COUNT or point k6 at real product_ids if needed.")


if __name__ == "__main__":
    main()
