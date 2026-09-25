import { describe, expect, it } from 'vitest'
import {
  validateEmail,
  validateLoginIdentifier,
  validatePassword,
  validatePasswordMatch,
  validateUsername,
} from '@/utils/validation'

describe('auth validation', () => {
  it('rejects short or illegal usernames', () => {
    expect(validateUsername('')).toBe('Username is required.')
    expect(validateUsername('ab')).toMatch(/3–32/)
    expect(validateUsername('bad name')).toMatch(/underscores/)
    expect(validateUsername('player_1')).toBeUndefined()
  })

  it('rejects invalid emails and short passwords', () => {
    expect(validateEmail('nope')).toMatch(/valid email/)
    expect(validateEmail('a@b.com')).toBeUndefined()
    expect(validatePassword('short')).toMatch(/8–72/)
    expect(validatePassword('password1')).toBeUndefined()
    expect(validateLoginIdentifier('  ')).toMatch(/required/)
  })

  it('requires matching confirmation', () => {
    expect(validatePasswordMatch('password1', 'password2')).toMatch(/do not match/)
    expect(validatePasswordMatch('password1', 'password1')).toBeUndefined()
  })
})
