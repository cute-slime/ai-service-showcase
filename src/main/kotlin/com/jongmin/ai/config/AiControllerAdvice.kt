package com.jongmin.ai.config

import com.jongmin.jspring.web.controller.JControllerAdvice
import org.springframework.web.bind.annotation.ControllerAdvice

/**
 * AI Service 전역 예외 처리
 *
 * JControllerAdvice를 상속받아 공통 예외 처리를 적용
 *
 * @author jongmin
 * @since 2026. 01. 20
 */
@ControllerAdvice
class AiControllerAdvice : JControllerAdvice()
