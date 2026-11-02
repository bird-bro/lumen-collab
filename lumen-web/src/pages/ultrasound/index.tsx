import { Result } from 'antd'

/**
 * Ultrasound department route module.
 *
 * Placeholder per `init-web-skeleton` design D7 and `specs/web-app-shell`
 * § Placeholder Routes Declare Their State: it renders an explicit
 * not-yet-implemented state and MUST NOT request data or imply a delivered
 * capability - this module issues no network request and imports nothing from
 * the API seam.
 */
export default function UltrasoundPage() {
  return (
    <Result
      status="info"
      title="Ultrasound is not yet implemented"
      subTitle="This department module ships with a later change; no data is requested here."
    />
  )
}
