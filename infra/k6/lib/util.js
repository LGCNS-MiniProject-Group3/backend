// VU마다 자기 계정을 쓴다. 풀 크기를 최대 VU 수와 맞춰 두면 계정이 겹치지 않는다.
export function userFor(users) {
  return users[(__VU - 1) % users.length];
}

export function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

// 사람이 화면을 보는 시간(초)
export function think(min, max) {
  return min + Math.random() * (max - min);
}
