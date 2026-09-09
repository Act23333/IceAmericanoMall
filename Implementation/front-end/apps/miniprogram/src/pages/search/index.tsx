import { View, Text, Input } from '@tarojs/components';
import Taro, { useLoad } from '@tarojs/taro';
import { useState } from 'react';
import { apiClient } from '../../utils/api';
import ProductCard from '../../components/product-card';

export default function SearchPage() {
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState<any[]>([]);
  const [total, setTotal] = useState(0);
  const [hotWords, setHotWords] = useState<string[]>([]);

  useLoad((options) => {
    fetchHotWords();
    if (options?.keyword) {
      const kw = decodeURIComponent(options.keyword);
      setKeyword(kw);
      doSearch(kw);
    }
  });

  async function fetchHotWords() {
    try { const data = await apiClient<string[]>('/api/search/hot?limit=8'); setHotWords(data || []); } catch {}
  }

  async function doSearch(kw: string) {
    if (!kw.trim()) return;
    try {
      const data = await apiClient<any>(`/api/search/product?keyword=${encodeURIComponent(kw)}&page=1&size=20`);
      setResults(data?.records || []);
      setTotal(data?.total || 0);
    } catch {}
  }

  return (
    <View className="page">
      <View className="search-bar">
        <Input className="search-input" value={keyword} onInput={(e: any) => setKeyword(e.detail.value)}
          onConfirm={() => doSearch(keyword)} placeholder="搜索商品..." />
        <Text className="search-btn" onClick={() => doSearch(keyword)}>搜索</Text>
      </View>

      {total > 0 && <Text className="result-count">共 {total} 件</Text>}

      {results.length > 0 ? (
        <View className="product-grid">
          {results.map((p: any) => <ProductCard key={p.id} product={p} />)}
        </View>
      ) : keyword ? (
        <View className="empty"><Text>未找到相关商品</Text></View>
      ) : (
        <View className="hot-section">
          <Text className="hot-title">🔥 热门搜索</Text>
          <View className="hot-tags">
            {hotWords.map((w) => (
              <Text key={w} className="hot-tag" onClick={() => { setKeyword(w); doSearch(w); }}>{w}</Text>
            ))}
          </View>
        </View>
      )}
    </View>
  );
}
