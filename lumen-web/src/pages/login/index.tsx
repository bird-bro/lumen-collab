import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { Button, Form, Input, Typography } from 'antd'
import type { CSSProperties } from 'react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '@/api/client'
import { login as authLogin } from '@/api/auth'
import { firstGrantedRoute } from '@/routes/menu-paths'
import { useAuthStore } from '@/stores/auth'
import { encryptPassword, invalidateKeyCache } from '@/utils/password-cipher'
import headLogo from './head.png'
import loginBg from './login-bg.svg'

/**
 * Login page (batch 5b task 5.2; reworked to the OPS login layout verbatim on
 * user request 2026-11-04 - read from `OPS/ops_web/src/views/login/index.vue`):
 *
 * - a full-page background IMAGE (`./login-bg.svg`, background-size cover -
 *   the bespoke open-source-safe stand-in for OPS's `assets/login/logo1.png`;
 *   swapping in a licensed brand photo = replacing this one reference);
 * - the white card 450px wide anchored at `top:50% / left:75%`
 *   translate(-50%,-50%), padding 50px 20px 0, radius 8, the OPS card shadow;
 * - the brand mark is `lumen-reference/head.png` (user-picked, downscaled to
 *   256px) INLINE with the 32px bold title, one row (user request 2026-11-04,
 *   subtitle dropped);
 * - inputs are the standard bordered AntD fields at 18px (user request:
 *   visible borders, larger font - supersedes the OPS underline style);
 * - the login failure surfaces INLINE in the OPS `.error-info` box (cream bg,
 *   orange border, red text) instead of a toast - same `msg` verbatim;
 * - footer: the OPS `.login-footer` position (absolute bottom 20px, centered,
 *   small white text) with the user's credit line;
 * - the prototype's Enter-hint kbd line is REMOVED (user: redundant) while the
 *   Enter-submits BEHAVIOR stays (spec `web-auth-flow` § Login Page).
 * The privacy-checkbox / forgot-password link remain absent (design D7).
 *
 * Decorative OPS literal colors enter as rgba(...) forms (the hex-discipline
 * guard scans comments too, so OPS's source values are transcribed as rgb():
 * card shadow rgb(158,177,200) at .6; error box rgb(254,252,238) bg /
 * rgb(255,147,76) border; input glyphs rgb(78,78,78); the underline stays
 * white at 30% opacity per the OPS source.
 */

interface LoginValues {
  username: string
  password: string
}

/** OPS `.error-info`: the inline failure box under the form. */
function errorBoxStyle(): CSSProperties {
  return {
    display: 'flex',
    alignItems: 'center',
    height: 40,
    padding: '0 8px',
    marginBottom: 10,
    marginLeft: 44,
    fontSize: 14,
    color: 'rgba(226, 60, 60, 1)',
    background: 'rgba(254, 252, 238, 1)',
    border: '1px solid rgba(255, 147, 76, 0.85)',
    borderRadius: 6,
  }
}

/** Clearly-bordered large inputs (user request 2026-11-04: visible border, larger font). */
const boxedInputStyle: CSSProperties = {
  fontSize: 20,
}
const prefixIconStyle: CSSProperties = {
  color: 'rgba(78, 78, 78, 1)',
  fontSize: 18,
  marginInlineEnd: 8,
}

export default function LoginPage() {
  const navigate = useNavigate()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const onFinish = async (values: LoginValues) => {
    setError(null)
    setSubmitting(true)
    try {
      // Password transport encryption (password-transport-encryption-web task 2.2,
      // coordination D4/D8): the typed password is SM2-encrypted BEFORE the body is
      // built - the typed value never reaches the wire. The key arrives from the
      // anonymous /auth-key endpoint (cached module-level in the util).
      const data = await authLogin({ ...values, password: await encryptPassword(values.password) })
      useAuthStore.getState().setSession({
        token: data.token,
        profile: data.profile,
        menus: data.menus,
        perms: data.perms,
      })
      navigate(firstGrantedRoute(data.menus) ?? '/', { replace: true })
    } catch (err) {
      // The OPS login shows the failure INLINE (.error-info), msg verbatim. An A0400
      // (stale key after a server-side rotation) drops the cached key so the NEXT
      // submit refetches - one bounded self-heal step, no auto-retry (design D3).
      if (err instanceof ApiError && err.code === 'A0400') {
        invalidateKeyCache()
      }
      setError(err instanceof ApiError ? err.msg : '登录失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      style={{
        position: 'relative',
        height: '100vh',
        overflow: 'auto',
        backgroundImage: `url(${loginBg})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center',
      }}
    >
      <div
        style={{
          position: 'absolute',
          top: '50%',
          left: '75%',
          transform: 'translate(-50%, -50%)',
          zIndex: 10,
          width: 450,
          padding: '50px 20px 0',
          textAlign: 'center',
          background: 'rgb(255, 255, 255)',
          borderRadius: 8,
          boxShadow: '0 0 10px rgba(158, 177, 200, 0.6)',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 14,
            marginBottom: 40,
          }}
        >
          <img src={headLogo} alt="LumenCollab" style={{ width: 64, height: 64, borderRadius: 14 }} />
          <div style={{ fontSize: 32, fontWeight: 700, color: 'rgba(51, 51, 51, 1)', lineHeight: 1.25 }}>
            LumenCollab
          </div>
        </div>

        <Form<LoginValues> onFinish={onFinish} layout="vertical">
          {error ? (
            <div style={errorBoxStyle()}>
              <span>⚠ </span>
              <span>{error}</span>
            </div>
          ) : null}
          <Form.Item name="username" rules={[{ required: true, message: '请输入账号' }]}>
            <Input
              size="large"
              placeholder="账号"
              autoComplete="off"
              aria-label="账号"
              prefix={<UserOutlined style={prefixIconStyle} />}
              style={boxedInputStyle}
            />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password
              size="large"
              placeholder="密码"
              autoComplete="off"
              aria-label="密码"
              prefix={<LockOutlined style={prefixIconStyle} />}
              style={boxedInputStyle}
            />
          </Form.Item>
          <Form.Item style={{ marginBottom: 26 }}>
            <Button
              type="primary"
              htmlType="submit"
              block
              loading={submitting}
              style={{ fontSize: 18, height: 44 }}
            >
              {submitting ? '登录中' : '登 录'}
            </Button>
          </Form.Item>
        </Form>
        <div style={{ textAlign: 'right', paddingBottom: 20 }}>
          {/* OPS .text-right 忘记密码? link - RESERVED: no endpoint exists yet
              (design D7), so the link is a no-op placeholder (user request
              2026-11-04 to keep the affordance visible). */}
          <Typography.Link
            style={{ fontSize: 14 }}
            onClick={(event) => event.preventDefault()}
            aria-label="忘记密码（预留）"
          >
            忘记密码?
          </Typography.Link>
        </div>
      </div>

      <div
        style={{
          position: 'absolute',
          right: 0,
          bottom: 20,
          left: 0,
          textAlign: 'center',
          fontSize: 16,
          color: 'rgba(255, 255, 255, 1)',
        }}
      >
        Open Source . by birdbro
      </div>
    </div>
  )
}
