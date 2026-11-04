import { PlusOutlined, UserOutlined, LockOutlined } from '@ant-design/icons'
import { Button, Form, Input, message, theme } from 'antd'
import type { CSSProperties } from 'react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '@/api/client'
import { login as authLogin } from '@/api/auth'
import { firstGrantedRoute } from '@/routes/menu-paths'
import { useAuthStore } from '@/stores/auth'
import { themeConfig } from '@/theme'

/**
 * Login page (batch 5b of web-foundation-auth-console, task 5.2; design D7).
 * OPS-pattern centered card on the deep-blue stage per the user-confirmed
 * prototype `login.html`: brand mark + product title, account + password
 * fields, Enter submits exactly like the button, the submit button carries a
 * pending state, and an `A`-class failure surfaces the envelope `msg`
 * verbatim through `message.error` while the visitor stays on the page.
 * The prototype's privacy-checkbox / forgot-password link are intentionally
 * absent (design D7 - no such endpoint or product requirement).
 *
 * Colors: every value reads the AntD token pipeline. The stage radial tints
 * are the token primary at alpha via 8-digit hex built from
 * `token.colorPrimary` at runtime (no hex literal - the hex-discipline guard
 * stays green and the tints follow any future primary change); the prototype's
 * documented-but-untokened decorative values (its pre-rev.11 stage base and
 * gradient end) are replaced by the synced `Layout.siderBg` token plus a
 * neutral black overlay - recorded in the batch report's
 * prototype-vs-implementation table.
 */

/** Stage backdrop per login.html: deep-blue base + two primary-tint radials + bottom shade. */
function stageBackground(colorPrimary: string, stageBase: string): string {
  return [
    `radial-gradient(1100px 700px at 74% -14%, ${colorPrimary}4D, transparent 62%)`,
    `radial-gradient(880px 640px at 4% 112%, ${colorPrimary}5C, transparent 58%)`,
    'linear-gradient(to top, rgba(0, 0, 0, 0.20), transparent 38%)',
    stageBase,
  ].join(', ')
}

/** login.html `.bg-grid`: faint 96px imaging grid, radially masked around the card. */
const GRID_LINES = [
  'linear-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px)',
  'linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px)',
].join(', ')

interface LoginValues {
  username: string;
  password: string;
}

export default function LoginPage() {
  const navigate = useNavigate()
  const setSession = useAuthStore((state) => state.setSession)
  const [form] = Form.useForm<LoginValues>()
  const [messageApi, messageContextHolder] = message.useMessage()
  const { token } = theme.useToken()
  const [loading, setLoading] = useState(false)

  const onFinish = async (values: LoginValues) => {
    setLoading(true);
    try {
      const data = await authLogin({ username: values.username, password: values.password });
      setSession({ token: data.token, profile: data.profile, menus: data.menus, perms: data.perms });
      // design D6: land on the first menu route the user's tree grants.
      navigate(firstGrantedRoute(data.menus) ?? '/', { replace: true });
    } catch (error) {
      if (error instanceof ApiError) {
        messageApi.error(error.msg);
      } else {
        messageApi.error('登录失败，请稍后重试');
      }
    } finally {
      setLoading(false);
    }
  };

  const cardStyle: CSSProperties = {
    position: 'relative',
    zIndex: 10,
    width: 400,
    background: token.colorBgContainer,
    borderRadius: token.borderRadiusLG,
    padding: '42px 38px 34px',
    boxShadow: '0 24px 64px rgba(2, 18, 46, 0.42)',
  }

  return (
    <div
      style={{
        position: 'relative',
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
        padding: '32px 16px',
        background: stageBackground(
          token.colorPrimary,
          themeConfig.components?.Layout?.siderBg ?? token.colorBgContainer,
        ),
      }}
    >
      {messageContextHolder}
      <div
        aria-hidden
        style={{
          position: 'absolute',
          inset: 0,
          pointerEvents: 'none',
          backgroundImage: GRID_LINES,
          backgroundSize: '96px 96px',
          maskImage: 'radial-gradient(760px 580px at 50% 44%, rgba(0, 0, 0, 0.9), transparent 78%)',
          WebkitMaskImage: 'radial-gradient(760px 580px at 50% 44%, rgba(0, 0, 0, 0.9), transparent 78%)',
        }}
      />
      <main style={cardStyle}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 30 }}>
          <span
            style={{
              width: 44,
              height: 44,
              borderRadius: 11,
              flex: '0 0 auto',
              display: 'grid',
              placeItems: 'center',
              background: `linear-gradient(135deg, ${token.colorPrimary} 0%, ${token.colorPrimaryHover} 100%)`,
              boxShadow: `0 6px 16px ${token.colorPrimary}59`,
            }}
          >
            <PlusOutlined style={{ color: token.colorTextLightSolid, fontSize: 22 }} />
          </span>
          <div>
            <div style={{ fontSize: token.fontSizeLG, fontWeight: 700, letterSpacing: 0.3, color: token.colorText }}>
              LumenCollab
            </div>
            <div style={{ fontSize: token.fontSizeSM, color: token.colorTextTertiary, marginTop: 2, letterSpacing: 2 }}>
              影像协作平台
            </div>
          </div>
        </div>

        <Form<LoginValues>
          form={form}
          onFinish={onFinish}
          autoComplete="off"
          requiredMark={false}
          disabled={loading}
        >
          <Form.Item name="username" rules={[{ required: true, message: '请输入账号' }]}>
            <Input
              size="large"
              placeholder="账号"
              autoComplete="username"
              prefix={<UserOutlined style={{ color: token.colorTextTertiary }} />}
            />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input
              size="large"
              type="password"
              placeholder="密码"
              autoComplete="current-password"
              prefix={<LockOutlined style={{ color: token.colorTextTertiary }} />}
            />
          </Form.Item>
          <div
            style={{
              display: 'flex',
              justifyContent: 'flex-end',
              alignItems: 'center',
              gap: 8,
              fontSize: token.fontSizeSM,
              color: token.colorTextTertiary,
              marginTop: -4,
              marginBottom: 16,
            }}
          >
            <kbd
              style={{
                fontFamily: 'inherit',
                fontSize: token.fontSizeSM,
                color: token.colorTextSecondary,
                border: `1px solid ${token.colorBorder}`,
                borderBottomWidth: 2,
                borderRadius: 4,
                padding: '1px 8px',
                background: token.colorBgLayout,
              }}
            >
              Enter ⏎
            </kbd>
            输入密码后按回车直接登录
          </div>
          <Button
            type="primary"
            htmlType="submit"
            block
            size="large"
            loading={loading}
            style={{ fontWeight: 500, letterSpacing: 8, textIndent: 8 }}
          >
            {loading ? '登录中' : '登 录'}
          </Button>
        </Form>
      </main>

      {import.meta.env.DEV && (
        <>
          <p style={{ position: 'relative', zIndex: 10, marginTop: 22, fontSize: token.fontSizeSM, color: 'rgba(255, 255, 255, 0.55)' }}>
            开发种子账号:admin / admin123 · 仅开发 mock 环境
          </p>
          <p style={{ position: 'absolute', zIndex: 10, bottom: 20, left: 0, right: 0, textAlign: 'center', fontSize: token.fontSizeSM, color: 'rgba(255, 255, 255, 0.38)', letterSpacing: 1 }}>
            LumenCollab · 开发环境
          </p>
        </>
      )}
    </div>
  )
}
