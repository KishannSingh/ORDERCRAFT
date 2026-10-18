export const API_BASE_URL = 'http://localhost:8081';

export function apiErrorMessage(error: any, fallback: string): string {
  const body = error?.error;
  if (typeof body === 'string' && body.trim()) return body;
  if (body && typeof body === 'object') {
    const message = body.message ?? body.detail;
    if (typeof message === 'string' && message.trim()) return message;
  }
  if (error?.status === 0) return 'Cannot connect to the backend. Check that it is running on port 8081.';
  if (error?.status === 401) return 'Your session is invalid or expired. Sign in again.';
  if (error?.status === 403) return 'You do not have permission to perform this action.';
  if (error?.status === 404) return 'The requested record was not found.';
  if (error?.status === 409) return 'This change conflicts with records that already exist.';
  return `${fallback}${error?.status ? ` (HTTP ${error.status})` : ''}`;
}
