// 여러 시나리오가 함께 쓰는 사용자 지표
import { Counter } from 'k6/metrics';

// 프로필 등록·수정 성공 = Kafka B(recu.profile.updated) 발행 1건. 결과의 rate가 초당 발행 수
export const kafkaB = new Counter('kafka_b_published');
