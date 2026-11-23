package com.dentalclinic

import com.dentalclinic.common.scheduler.MemberLevelReviewService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemberLevelReviewServiceTest {

    @Test
    fun `年度会员复评只计入已支付成功的消费`() {
        assertTrue(MemberLevelReviewService.isPaidConsumption("success", "success"))
        assertFalse(MemberLevelReviewService.isPaidConsumption("success", "pending"))
        assertFalse(MemberLevelReviewService.isPaidConsumption("refunded", "success"))
    }
}
