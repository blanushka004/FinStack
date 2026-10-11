package com.finstack.category;

import com.finstack.category.dto.CategoryCreateRequest;
import com.finstack.category.dto.CategoryResponse;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionCategoryService {

    private final TransactionCategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public TransactionCategoryService(
            TransactionCategoryRepository categoryRepository,
            UserRepository userRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(CategoryType categoryType) {
        User user = getAuthenticatedUser();
        return categoryRepository.findAvailableForUser(user.getId(), categoryType).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        User user = getAuthenticatedUser();
        if (categoryRepository.existsByNameAndCategoryTypeAndUserId(
                request.name(), request.categoryType(), user.getId()
        )) {
            throw new CategoryAlreadyExistsException();
        }

        TransactionCategory category = new TransactionCategory(request.name(), request.categoryType(), user);
        return CategoryResponse.from(categoryRepository.save(category));
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }

        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Authenticated user is unavailable"));
    }
}
