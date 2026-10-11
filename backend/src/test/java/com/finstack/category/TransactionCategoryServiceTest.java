package com.finstack.category;

import com.finstack.category.dto.CategoryCreateRequest;
import com.finstack.category.dto.CategoryResponse;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionCategoryServiceTest {

    @Mock
    private TransactionCategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    private TransactionCategoryService categoryService;
    private User currentUser;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        categoryService = new TransactionCategoryService(categoryRepository, userRepository);
        currentUserId = UUID.randomUUID();
        currentUser = new User("Ada", "Lovelace", "ada@example.com", "password-hash", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(currentUser, "id", currentUserId);
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(currentUser));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("ada@example.com", null, List.of())
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsSystemAndCurrentUsersCustomCategories() {
        TransactionCategory systemCategory = new TransactionCategory("FOOD", CategoryType.EXPENSE, null);
        ReflectionTestUtils.setField(systemCategory, "systemDefined", true);
        TransactionCategory customCategory = new TransactionCategory("PET_CARE", CategoryType.EXPENSE, currentUser);
        when(categoryRepository.findAvailableForUser(currentUserId, CategoryType.EXPENSE))
                .thenReturn(List.of(systemCategory, customCategory));

        List<CategoryResponse> categories = categoryService.list(CategoryType.EXPENSE);

        assertThat(categories).extracting(CategoryResponse::name).containsExactly("FOOD", "PET_CARE");
        assertThat(categories.getFirst().systemDefined()).isTrue();
    }

    @Test
    void createsCustomCategoryOnlyForCurrentUser() {
        CategoryCreateRequest request = new CategoryCreateRequest(" Pet Care ", CategoryType.EXPENSE);
        when(categoryRepository.existsByNameAndCategoryTypeAndUserId("PET CARE", CategoryType.EXPENSE, currentUserId))
                .thenReturn(false);
        when(categoryRepository.save(any(TransactionCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.create(request);

        ArgumentCaptor<TransactionCategory> categoryCaptor = ArgumentCaptor.forClass(TransactionCategory.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getUser()).isSameAs(currentUser);
        assertThat(categoryCaptor.getValue().isSystemDefined()).isFalse();
        assertThat(response.name()).isEqualTo("PET CARE");
    }

    @Test
    void rejectsDuplicateCustomCategoryForTheSameUserAndType() {
        CategoryCreateRequest request = new CategoryCreateRequest("Food", CategoryType.EXPENSE);
        when(categoryRepository.existsByNameAndCategoryTypeAndUserId("FOOD", CategoryType.EXPENSE, currentUserId))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(CategoryAlreadyExistsException.class);
    }
}
