import { Result } from 'antd'
import { theme } from 'antd'

/**
 * Explicit not-implemented state for console routes created ahead of their
 * feature (batch 5b task 5.3; `specs/web-app-shell` § Placeholder Routes
 * Declare Their State): the route renders this state and MUST NOT fetch data
 * or imply a delivered capability - the batch-6 console pages replace these
 * files in place (`src/pages/system/<resource>/index.tsx`).
 */
export function ConsolePlaceholder({ title }: { title: string }) {
  const { token } = theme.useToken()
  return (
    <div style={{ background: token.colorBgContainer, borderRadius: token.borderRadiusLG }}>
      <Result
        status="info"
        title={`${title} is not yet implemented`}
        subTitle="This console page ships with a later batch; no data is requested here."
      />
    </div>
  )
}
