package com.jongmin.ai.role_playing

import com.jongmin.ai.role_playing.platform.entity.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.querydsl.QuerydslPredicateExecutor
import org.springframework.stereotype.Repository

// JpaRepository, CrudRepository 거의 유사한 두 상위 인터페이스 중 무엇을 사용할것인지 결정이 필요하다.
// 결정은 JpaRepository 왜냐 findAll 이 반환하는 인터페이스가 List vs Iterable 이다.
@Repository
interface RolePlayingRepository : JpaRepository<RolePlaying, Long>, QuerydslPredicateExecutor<RolePlaying>

@Repository
interface WorldviewRepository : JpaRepository<Worldview, Long>, QuerydslPredicateExecutor<Worldview>

@Repository
interface StageRepository : JpaRepository<Stage, Long>, QuerydslPredicateExecutor<Stage>

@Repository
interface AiCharacterRepository : JpaRepository<AiCharacter, Long>, QuerydslPredicateExecutor<AiCharacter>

@Repository
interface PlaceRepository : JpaRepository<Place, Long>, QuerydslPredicateExecutor<Place>

@Repository
interface RpLogRepository : JpaRepository<RpLog, Long>, QuerydslPredicateExecutor<RpLog>
