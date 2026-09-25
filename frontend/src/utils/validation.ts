export function validateUsername(value: string): string | undefined {
  const trimmed = value.trim()
  if (!trimmed) {
    return 'Username is required.'
  }
  if (trimmed.length < 3 || trimmed.length > 32) {
    return 'Username must be 3–32 characters.'
  }
  if (!/^[A-Za-z0-9_]+$/.test(trimmed)) {
    return 'Username may contain letters, numbers, and underscores only.'
  }
  return undefined
}

export function validateEmail(value: string): string | undefined {
  const trimmed = value.trim()
  if (!trimmed) {
    return 'Email is required.'
  }
  if (trimmed.length > 255 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
    return 'Enter a valid email address.'
  }
  return undefined
}

export function validatePassword(value: string): string | undefined {
  if (!value) {
    return 'Password is required.'
  }
  if (value.length < 8 || value.length > 72) {
    return 'Password must be 8–72 characters.'
  }
  return undefined
}

export function validateLoginIdentifier(value: string): string | undefined {
  if (!value.trim()) {
    return 'Username or email is required.'
  }
  return undefined
}

export function validatePasswordMatch(password: string, confirm: string): string | undefined {
  if (!confirm) {
    return 'Confirm your password.'
  }
  if (password !== confirm) {
    return 'Passwords do not match.'
  }
  return undefined
}
