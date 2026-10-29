-- Phase 2 收尾：产品路由只保留 model_product_group；旧 target/route 已无代码引用。
DROP TABLE IF EXISTS model_route;
DROP TABLE IF EXISTS model_target;
