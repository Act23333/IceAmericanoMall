import { View, Text, Input, Button, Image } from '@tarojs/components';
import Taro from '@tarojs/taro';
import { useState } from 'react';
import { apiClient, setTokens, saveUser, getSavedUser } from '../../utils/api';

export default function LoginPage() {
  const [mode, setMode] = useState<'wechat' | 'password' | 'sms' | 'register'>('wechat');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [username, setUsername] = useState('');
  const [loading, setLoading] = useState(false);
  const [wxUser, setWxUser] = useState<{ nickName: string; avatarUrl: string } | null>(null);
  const [authError, setAuthError] = useState('');

  // ==================== 微信一键登录 ====================

  async function handleWechatLogin() {
    setLoading(true);
    setAuthError('');
    try {
      // 1. wx.login 获取临时 code
      const loginRes = await Taro.login();
      if (!loginRes.code) {
        setAuthError('获取微信登录码失败，请在微信中打开');
        setLoading(false);
        return;
      }

      // 2. 获取用户信息（头像+昵称）
      let nickName = '微信用户';
      let avatarUrl = '';
      try {
        // 新版微信: wx.getUserProfile 需要用户主动触发
        // 这里使用 getUserInfo 获取已授权的信息
        const setting = await Taro.getSetting();
        if (setting.authSetting['scope.userInfo']) {
          const userInfo = await Taro.getUserInfo();
          nickName = userInfo.userInfo.nickName || '微信用户';
          avatarUrl = userInfo.userInfo.avatarUrl || '';
          setWxUser({ nickName, avatarUrl });
        }
      } catch {
        // 未授权时也可登录，使用默认昵称
      }

      // 3. 发送 code 到后端
      const res = await apiClient<any>('/api/auth/login/wechat', {
        method: 'POST',
        data: { code: loginRes.code },
      });

      // 4. 保存 token + 用户信息
      setTokens(res.access_token, res.refresh_token);

      // 5. 获取完整用户信息
      try {
        const userInfo = await apiClient<any>('/api/user/info');
        saveUser(userInfo);
      } catch {}

      // 6. 保存微信昵称头像(如果后端没存)
      if (nickName !== '微信用户' || avatarUrl) {
        saveUser({ nickName, avatarUrl, ...getSavedUser() });
      }

      Taro.showToast({ title: '登录成功', icon: 'success' });
      setTimeout(() => Taro.navigateBack(), 800);
    } catch (e: any) {
      setAuthError(e?.message || '登录失败，请重试');
    } finally {
      setLoading(false);
    }
  }

  // ==================== 获取微信头像昵称 ====================

  async function handleGetUserProfile() {
    try {
      const res = await Taro.getUserProfile({ desc: '用于完善个人资料' });
      setWxUser({
        nickName: res.userInfo.nickName,
        avatarUrl: res.userInfo.avatarUrl,
      });
    } catch (e: any) {
      if (e.errMsg?.includes('cancel')) return;
      Taro.showToast({ title: '获取失败', icon: 'none' });
    }
  }

  // ==================== 手机号密码登录 ====================

  async function handlePasswordLogin() {
    if (!phone) { Taro.showToast({ title: '请输入手机号', icon: 'none' }); return; }
    if (!password) { Taro.showToast({ title: '请输入密码', icon: 'none' }); return; }
    setLoading(true);
    setAuthError('');
    try {
      const res = await apiClient<any>('/api/auth/login', {
        method: 'POST',
        data: { identityType: 'PHONE', credentialType: 'PASSWORD', account: phone, credential: password },
      });
      setTokens(res.access_token, res.refresh_token);
      try {
        const userInfo = await apiClient<any>('/api/user/info');
        saveUser(userInfo);
      } catch {}
      Taro.showToast({ title: '登录成功', icon: 'success' });
      setTimeout(() => Taro.navigateBack(), 800);
    } catch (e: any) {
      setAuthError(e?.message || '登录失败');
    } finally {
      setLoading(false);
    }
  }

  // ==================== 手机号验证码登录 ====================

  async function handleSmsLogin() {
    if (!phone) { Taro.showToast({ title: '请输入手机号', icon: 'none' }); return; }
    if (!code) { Taro.showToast({ title: '请输入验证码', icon: 'none' }); return; }
    setLoading(true);
    setAuthError('');
    try {
      const res = await apiClient<any>('/api/auth/login', {
        method: 'POST',
        data: { identityType: 'PHONE', credentialType: 'SMS_CODE', account: phone, credential: code },
      });
      setTokens(res.access_token, res.refresh_token);
      try { saveUser(await apiClient<any>('/api/user/info')); } catch {}
      Taro.showToast({ title: '登录成功', icon: 'success' });
      setTimeout(() => Taro.navigateBack(), 800);
    } catch (e: any) {
      setAuthError(e?.message || '登录失败');
    } finally {
      setLoading(false);
    }
  }

  // ==================== 注册 ====================

  async function handleRegister() {
    if (!phone) { Taro.showToast({ title: '请输入手机号', icon: 'none' }); return; }
    if (!password) { Taro.showToast({ title: '请设置密码', icon: 'none' }); return; }
    if (!code) { Taro.showToast({ title: '请输入验证码', icon: 'none' }); return; }
    setLoading(true);
    setAuthError('');
    try {
      await apiClient('/api/auth/register', {
        method: 'POST',
        data: { phone, password, code, username: username || undefined },
      });
      // 注册成功后自动登录
      const res = await apiClient<any>('/api/auth/login', {
        method: 'POST',
        data: { identityType: 'PHONE', credentialType: 'PASSWORD', account: phone, credential: password },
      });
      setTokens(res.access_token, res.refresh_token);
      try { saveUser(await apiClient<any>('/api/user/info')); } catch {}
      Taro.showToast({ title: '注册成功', icon: 'success' });
      setTimeout(() => Taro.navigateBack(), 800);
    } catch (e: any) {
      setAuthError(e?.message || '注册失败');
    } finally {
      setLoading(false);
    }
  }

  async function sendCode() {
    if (!phone) { Taro.showToast({ title: '请输入手机号', icon: 'none' }); return; }
    try {
      await apiClient('/api/user/code', { method: 'POST', data: { phone } });
      Taro.showToast({ title: '验证码已发送', icon: 'none' });
    } catch {}
  }

  // ==================== 渲染 ====================

  return (
    <View className="page login-page">
      <View className="login-card">
        {/* 品牌 Logo */}
        <View className="brand-header">
          <Text className="brand-name">冰美商城</Text>
          <Text className="brand-slogan">科技自然融入生活</Text>
        </View>

        {/* 微信一键登录区域 */}
        <View className="wechat-section">
          {/* 用户头像昵称 */}
          <View className="wx-user-info">
            {wxUser?.avatarUrl ? (
              <Image src={wxUser.avatarUrl} className="wx-avatar" mode="aspectFill" />
            ) : (
              <View className="wx-avatar-placeholder">
                <Text>👤</Text>
              </View>
            )}
            <Text className="wx-nickname">{wxUser?.nickName || '微信用户'}</Text>
          </View>

          {/* 微信登录按钮 */}
          <Button
            className="wechat-login-btn"
            loading={loading}
            onClick={handleWechatLogin}
          >
            <Text className="wechat-icon">💚</Text>
            <Text>微信一键登录</Text>
          </Button>

          {/* 获取微信头像昵称 */}
          <View className="get-profile-btn" onClick={handleGetUserProfile}>
            <Text>点击获取微信头像和昵称</Text>
          </View>

          {authError && <Text className="error-text">{authError}</Text>}
        </View>

        {/* 分隔线 */}
        <View className="divider">
          <View className="divider-line" />
          <Text className="divider-text">其他方式</Text>
          <View className="divider-line" />
        </View>

        {/* 登录方式切换 */}
        <View className="tab-row">
          <Text className={`tab ${mode === 'password' ? 'active' : ''}`} onClick={() => setMode('password')}>
            密码登录
          </Text>
          <Text className={`tab ${mode === 'sms' ? 'active' : ''}`} onClick={() => setMode('sms')}>
            验证码登录
          </Text>
          <Text className={`tab ${mode === 'register' ? 'active' : ''}`} onClick={() => setMode('register')}>
            注册
          </Text>
        </View>

        {/* 手机号输入 */}
        {mode !== 'wechat' && (
          <>
            <Input
              className="input"
              value={phone}
              onInput={(e: any) => setPhone(e.detail.value)}
              placeholder="手机号"
              type="number"
              maxlength={11}
            />

            {mode === 'register' && (
              <Input
                className="input"
                value={username}
                onInput={(e: any) => setUsername(e.detail.value)}
                placeholder="用户名（选填）"
                maxlength={20}
              />
            )}

            {mode === 'password' || mode === 'register' ? (
              <Input
                className="input"
                value={password}
                onInput={(e: any) => setPassword(e.detail.value)}
                placeholder="密码"
                password
              />
            ) : null}

            {(mode === 'sms' || mode === 'register') && (
              <View className="sms-row">
                <Input
                  className="input flex-1"
                  value={code}
                  onInput={(e: any) => setCode(e.detail.value)}
                  placeholder="验证码"
                  type="number"
                />
                <View className="sms-btn" onClick={sendCode}>
                  <Text>获取验证码</Text>
                </View>
              </View>
            )}

            <Button
              className="login-btn"
              loading={loading}
              onClick={
                mode === 'password' ? handlePasswordLogin :
                mode === 'sms' ? handleSmsLogin :
                handleRegister
              }
            >
              {mode === 'register' ? '注册' : '登录'}
            </Button>
          </>
        )}
      </View>
    </View>
  );
}
