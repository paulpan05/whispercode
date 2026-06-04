// UPSTREAM-DIVERGENCE-FILE: The app package re-exports fork-only mobile push contracts added after
// upstream sync 6b9ce5e63. Future merges must keep this surface stable for packages/ios and
// packages/android, which consume the shared app package instead of re-declaring these types.

export { AppBaseProviders, AppInterface } from "./app"
export { ACCEPTED_FILE_EXTENSIONS, ACCEPTED_FILE_TYPES, filePickerFilters } from "./constants/file-picker"
export { useCommand } from "./context/command"
export { loadLocaleDict, normalizeLocale, type Locale, useLanguage } from "./context/language"
export {
  type DisplayBackend,
  type FatalRendererErrorLog,
  type NotifyOpts,
  type PairInfo,
  type PairState,
  type Platform,
  type PushCred,
  type PushDiag,
  type PushKind,
  type PushPerm,
  type PushPrefs,
  type PushState,
  PlatformProvider,
} from "./context/platform"
export { ServerConnection } from "./context/server"
export { handleNotificationClick } from "./utils/notification-click"
// UPSTREAM-DIVERGENCE: These helpers power the fork's relay-backed pairing flow from outside the app
// bundle, so removing them during an upstream sync would break mobile setup.
export {
  PushFail,
  pushIssue,
  type PushIssue,
  type PushIssueCode,
  type PushPhase,
  runPushSetup,
} from "./utils/push-pair"
