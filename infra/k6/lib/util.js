import exec from 'k6/execution';

// VU마다 자기 계정을 쓴다. 계정 수는 main.js의 USER_POOL(선택한 시나리오의 최대 VU 합)이라
// VU 번호가 시나리오를 넘어 이어져도 두 VU가 같은 계정을 쓰지 않는다.
export function userFor(users) {
  const i = exec.vu.idInTest - 1;
  if (i >= users.length) {
    exec.test.abort(`VU ${i + 1}번에 줄 계정이 없어요 (계정 ${users.length}개). *_PEAK_VUS 설정을 확인하세요.`);
  }
  return users[i];
}

// 읽기만 하는 계정(가이드 고정 계정)은 여러 VU가 나눠 써도 된다
export function sharedUserFor(users) {
  return users[(exec.vu.idInTest - 1) % users.length];
}

export function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

// 사람이 화면을 보는 시간(초)
export function think(min, max) {
  return min + Math.random() * (max - min);
}
