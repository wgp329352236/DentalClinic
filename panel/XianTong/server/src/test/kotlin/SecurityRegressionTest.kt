package com.dentalclinic

import com.dentalclinic.admin.auth.ClinicScope
import com.dentalclinic.admin.service.allowsClinic
import com.dentalclinic.admin.service.requireFullClinicScope
import com.dentalclinic.admin.util.ForbiddenException
import com.dentalclinic.common.security.PhoneCipherService
import com.dentalclinic.user.service.UserConsumptionPaymentService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClinicScopeGuardTest {

    @Test
    fun `全店范围允许任意门店和空门店`() {
        assertTrue(ClinicScope.ALL.allowsClinic(1))
        assertTrue(ClinicScope.ALL.allowsClinic(null))
    }

    @Test
    fun `受限范围只允许显式授权门店`() {
        val scope = ClinicScope(all = false, clinicIds = listOf(10, 20))

        assertTrue(scope.allowsClinic(10))
        assertTrue(scope.allowsClinic(20))
        assertFalse(scope.allowsClinic(30))
        assertFalse(scope.allowsClinic(null))
    }

    @Test
    fun `系统管理写入必须拥有全部门店范围`() {
        assertFailsWith<ForbiddenException> {
            requireFullClinicScope(
                ClinicScope(all = false, clinicIds = listOf(10)),
                "无权写入"
            )
        }
    }
}

class PhoneCipherServiceTest {

    @Test
    fun `手机号归一化兼容国家码和常见分隔符`() {
        assertEquals("13800138000", PhoneCipherService.normalize("+86 138-0013-8000"))
        assertEquals("13800138000", PhoneCipherService.normalize("86(138)0013 8000"))
        assertTrue(PhoneCipherService.isValidMainlandMobile("+86 138-0013-8000"))
    }

    @Test
    fun `手机号可加密解密且盲索引稳定`() {
        val first = PhoneCipherService.encrypt("138 0013 8000")
        val second = PhoneCipherService.encrypt("+86 13800138000")

        assertEquals("13800138000", PhoneCipherService.decrypt(first.ciphertext))
        assertEquals(first.blindIndex, second.blindIndex)
        assertNotEquals(first.ciphertext, second.ciphertext)
    }

    @Test
    fun `损坏密文不会回退为明文`() {
        assertNull(PhoneCipherService.decrypt("v1:broken:ciphertext"))
        assertNull(PhoneCipherService.readPhone("v1:broken:ciphertext", "13800138000"))
    }
}

class ConsumptionPaymentTradeNoTest {

    @Test
    fun `统一支付回调能区分消费单与充值单`() {
        assertTrue(UserConsumptionPaymentService.isConsumptionOutTradeNo("CS202611190001"))
        assertTrue(UserConsumptionPaymentService.isConsumptionOutTradeNo("CONSUMPTION_123"))
        assertFalse(UserConsumptionPaymentService.isConsumptionOutTradeNo("RC202611190001"))
        assertFalse(UserConsumptionPaymentService.isConsumptionOutTradeNo("PAY202611190001"))
    }
}
