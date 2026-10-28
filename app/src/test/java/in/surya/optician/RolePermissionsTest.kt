package `in`.surya.optician

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RolePermissionsTest {
    @Test
    fun ownerCanAccessAllSectionsAndManageRecords() {
        assertTrue(RolePermissions.isOwner("Owner"))
        assertTrue(RolePermissions.canManageRecords("Owner"))
        assertTrue(RolePermissions.canDeleteRecords("Owner"))
        assertTrue(RolePermissions.canAccess("Owner", "Users"))
        assertTrue(RolePermissions.canAccess("Owner", "Reports"))
    }

    @Test
    fun managerCanManageRecordsAndReportsButCannotDeleteOrManageUsers() {
        assertFalse(RolePermissions.isOwner("Manager"))
        assertTrue(RolePermissions.canManageRecords("Manager"))
        assertFalse(RolePermissions.canDeleteRecords("Manager"))
        assertFalse(RolePermissions.canAccess("Manager", "Users"))
        assertTrue(RolePermissions.canAccess("Manager", "Reports"))
        assertTrue(RolePermissions.canAccess("Manager", "Bills"))
    }

    @Test
    fun staffCanDoRoutineSectionsButCannotAccessReportsOrUsers() {
        assertFalse(RolePermissions.canManageRecords("Staff"))
        assertFalse(RolePermissions.canDeleteRecords("Staff"))
        assertFalse(RolePermissions.canAccess("Staff", "Users"))
        assertFalse(RolePermissions.canAccess("Staff", "Reports"))
        assertTrue(RolePermissions.canAccess("Staff", "Bills"))
        assertTrue(RolePermissions.canAccess("Staff", "New Bill"))
        assertTrue(RolePermissions.canAccess("Staff", "Payment"))
    }

    @Test
    fun unknownRoleGetsLeastPrivilegeForSensitiveSections() {
        assertFalse(RolePermissions.canManageRecords("unknown"))
        assertFalse(RolePermissions.canDeleteRecords("unknown"))
        assertFalse(RolePermissions.canAccess("unknown", "Users"))
        assertFalse(RolePermissions.canAccess("unknown", "Reports"))
    }
}
