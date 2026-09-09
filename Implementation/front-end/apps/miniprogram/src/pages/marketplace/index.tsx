import { View, Text, Input, ScrollView } from '@tarojs/components';
import Taro, { useLoad } from '@tarojs/taro';
import { useState } from 'react';
import { apiClient } from '../../utils/api';
import { MOCK_PRODUCTS } from '../../utils/mock';
import ProductCard from '../../components/product-card';
import './index.scss';

export default function MarketplacePage() {
  const [products, setProducts] = useState<any[]>([]);
  const [categories, setCategories] = useState<any[]>([]);
  const [activeCid, setActiveCid] = useState<number | null>(null);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  useLoad((options) => {
    // switchTab 不支持传参，通过 storage 桥接
    const urlCid = options?.categoryId ? Number(options.categoryId) : null;
    const storedCid = Taro.getStorageSync('activeCategoryId');
    const cid = urlCid || (storedCid ? Number(storedCid) : null);
    if (storedCid) Taro.removeStorageSync('activeCategoryId');
    setActiveCid(cid);
    fetchCategories();
    fetchProducts(cid, 1);
  });

  async function fetchCategories() {
    try {
      const data = await apiClient<any[]>('/api/item/category/tree');
      setCategories(data || []);
    } catch {}
  }

  async function fetchProducts(cid: number | null, p: number) {
    setLoading(true);
    try {
      const q = new URLSearchParams({ page: String(p), size: '20', sort: 'sales', order: 'desc' });
      if (cid) q.set('categoryId', String(cid));
      if (keyword) q.set('keyword', keyword);
      const data = await apiClient<any>(`/api/item/product/page?${q.toString()}`);
      setProducts(data?.records || []);
      setTotal(data?.total || 0);
      setPage(p);
    } catch {} finally { setLoading(false); }
  }

  function onCategoryTap(cid: number | null) {
    setActiveCid(cid);
    fetchProducts(cid, 1);
  }

  function onSearch() {
    if (keyword.trim()) {
      Taro.navigateTo({ url: `/pages/search/index?keyword=${encodeURIComponent(keyword.trim())}` });
    }
  }

  return (
    <View className="page">
      {/* Search bar */}
      <View className="search-bar">
        <Input
          className="search-input"
          value={keyword}
          onInput={(e: any) => setKeyword(e.detail.value)}
          onConfirm={onSearch}
          placeholder="搜索商品..."
        />
        <Text className="search-btn" onClick={onSearch}>搜索</Text>
      </View>

      {/* Categories */}
      <ScrollView scrollX className="cat-scroll">
        <View className={`cat-tag ${activeCid === null ? 'active' : ''}`} onClick={() => onCategoryTap(null)}>全部</View>
        {categories.map((cat: any) => (
          <View key={cat.id} className={`cat-tag ${activeCid === cat.id ? 'active' : ''}`} onClick={() => onCategoryTap(cat.id)}>
            {cat.name}
          </View>
        ))}
      </ScrollView>

      {/* Product count */}
      <Text className="result-count">共 {total} 件商品</Text>

      {/* Product grid */}
      {loading ? (
        <View className="loading"><Text>加载中...</Text></View>
      ) : (
        <View className="product-grid">
          {products.length > 0
            ? products.map((p: any) => <ProductCard key={p.id} product={p} />)
            : MOCK_PRODUCTS.map((p: any) => <ProductCard key={p.id} product={p} />)}
        </View>
      )}

      {/* Pagination */}
      {total > 20 && (
        <View className="pagination">
          <Text className="page-btn" onClick={() => { if (page > 1) fetchProducts(activeCid, page - 1); }}>上一页</Text>
          <Text className="page-num">{page} / {Math.ceil(total / 20)}</Text>
          <Text className="page-btn" onClick={() => { if (page < Math.ceil(total / 20)) fetchProducts(activeCid, page + 1); }}>下一页</Text>
        </View>
      )}
    </View>
  );
}

