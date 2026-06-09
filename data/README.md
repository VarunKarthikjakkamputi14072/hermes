# Dataset

The system seeds a synthetic 200-SKU catalogue automatically (see
`order-api/src/main/resources/seed/products.csv`), so it runs out of the box.

To run against the **real Olist catalogue** (~33k products / ~100k orders):

1. Download from Kaggle:
   https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce
2. Drop these two files into this folder:
   - `olist_products_dataset.csv`
   - `olist_order_items_dataset.csv`
3. With the stack running, load them:
   ```bash
   pip install psycopg2-binary
   python scripts/load_olist.py --data ./data
   ```

The CSVs are gitignored — they are large and not redistributed here.
