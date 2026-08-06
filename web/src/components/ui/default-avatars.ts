export interface DefaultAvatar {
  id: string
  name: string
  src: string
}

export const defaultAvatars: DefaultAvatar[] = [
  { id: 'fox', name: '小狐狸', src: '/avatars/avatar-fox.png' },
  { id: 'bear', name: '小熊', src: '/avatars/avatar-bear.png' },
  { id: 'cat', name: '小猫', src: '/avatars/avatar-cat.png' },
  { id: 'rabbit', name: '小兔', src: '/avatars/avatar-rabbit.png' },
  { id: 'penguin', name: '小企鹅', src: '/avatars/avatar-penguin.png' },
  { id: 'dog', name: '小狗', src: '/avatars/avatar-dog.png' },
  { id: 'dino', name: '小恐龙', src: '/avatars/avatar-dino.png' },
  { id: 'robot', name: '小机器人', src: '/avatars/avatar-robot.png' }
]
