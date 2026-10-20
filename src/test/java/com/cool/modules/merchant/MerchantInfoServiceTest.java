package com.cool.modules.merchant;

import com.cool.core.exception.CoolException;
import com.cool.modules.merchant.entity.MerchantCategoryEntity;
import com.cool.modules.merchant.entity.MerchantInfoEntity;
import com.cool.modules.merchant.service.MerchantCategoryService;
import com.cool.modules.merchant.service.impl.MerchantInfoServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MerchantInfoServiceTest {
    private MerchantInfoEntity merchant() {
        MerchantInfoEntity entity = new MerchantInfoEntity();
        entity.setName("测试商家");
        entity.setContactName("联系人");
        entity.setContactPhone("010-12345678");
        entity.setCategoryId(1L);
        entity.setStatus(1);
        return entity;
    }

    @Test
    void rejectsBlankContactBeforePersistence() {
        MerchantCategoryService categories = mock(MerchantCategoryService.class);
        MerchantInfoEntity entity = merchant();
        entity.setContactName("  ");
        assertThrows(CoolException.class, () -> new MerchantInfoServiceImpl(categories).add(entity));
        verifyNoInteractions(categories);
    }

    @Test
    void rejectsMissingCategory() {
        MerchantCategoryService categories = mock(MerchantCategoryService.class);
        assertThrows(CoolException.class, () -> new MerchantInfoServiceImpl(categories).add(merchant()));
    }

    @Test
    void rejectsDisabledCategoryOnCreation() {
        MerchantCategoryService categories = mock(MerchantCategoryService.class);
        MerchantCategoryEntity category = new MerchantCategoryEntity();
        category.setStatus(0);
        when(categories.getById(1L)).thenReturn(category);
        assertThrows(CoolException.class, () -> new MerchantInfoServiceImpl(categories).add(merchant()));
    }

    @Test
    void rejectsInvalidStatus() {
        MerchantCategoryService categories = mock(MerchantCategoryService.class);
        MerchantCategoryEntity category = new MerchantCategoryEntity();
        category.setStatus(1);
        when(categories.getById(1L)).thenReturn(category);
        MerchantInfoEntity entity = merchant();
        entity.setStatus(2);
        assertThrows(CoolException.class, () -> new MerchantInfoServiceImpl(categories).add(entity));
    }
}
