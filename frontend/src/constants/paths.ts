export const paths = {
  home: '/',
  login: '/login',
  register: '/register',
  lobby: '/lobby',
  profile: '/profile',
  play: (variant: string) => `/play/${variant}`,
  game: (gameId: string) => `/game/${gameId}`,
} as const
