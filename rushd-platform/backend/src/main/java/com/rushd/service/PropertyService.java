package com.rushd.service;

import com.rushd.dto.CreatePropertyRequest;
import com.rushd.dto.PropertyResponse;
import com.rushd.dto.UpdatePropertyRequest;
import com.rushd.entity.Property;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.PropertyType;
import com.rushd.entity.Role;
import com.rushd.entity.User;
import com.rushd.exception.InvalidPropertyQueryException;
import com.rushd.exception.PropertyNotFoundException;
import com.rushd.exception.UserNotFoundException;
import com.rushd.mapper.PropertyMapper;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import com.rushd.repository.specification.PropertySpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    public PropertyService(PropertyRepository propertyRepository,
                           UserRepository userRepository) {
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PropertyResponse createProperty(CreatePropertyRequest request,
                                           String authenticatedEmail) {
        User seller = findAuthenticatedUser(authenticatedEmail);

        if (seller.getRole() != Role.SELLER) {
            throw new AccessDeniedException("Only sellers can create properties");
        }

        Property property = new Property();
        property.setSeller(seller);
        PropertyMapper.applyCreateRequest(property, request);

        return PropertyMapper.toResponse(propertyRepository.save(property));
    }

    @Transactional
    public PropertyResponse updateProperty(Long id,
                                           UpdatePropertyRequest request,
                                           String authenticatedEmail) {
        User authenticatedUser = findAuthenticatedUser(authenticatedEmail);
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new PropertyNotFoundException(id));

        boolean isAdmin = authenticatedUser.getRole() == Role.ADMIN;
        boolean isOwner = authenticatedUser.getRole() == Role.SELLER
                && authenticatedUser.getId().equals(property.getSeller().getId());
        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("Only the property owner or an admin can update properties");
        }

        PropertyMapper.applyUpdateRequest(property, request);
        return PropertyMapper.toResponse(propertyRepository.save(property));
    }

    @Transactional(readOnly = true)
    public Page<PropertyResponse> listProperties(
            String city,
            String district,
            PropertyType type,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {
        validatePriceRange(minPrice, maxPrice);

        return propertyRepository.findAll(
                        PropertySpecification.activeProperties(
                                city, district, type, minPrice, maxPrice),
                        pageable)
                .map(PropertyMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PropertyResponse getProperty(Long id, String authenticatedEmail) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new PropertyNotFoundException(id));

        if (!isPubliclyVisible(property) && !canViewPrivateProperty(property, authenticatedEmail)) {
            throw new PropertyNotFoundException(id);
        }

        return PropertyMapper.toDetailsResponse(property);
    }

    private boolean isPubliclyVisible(Property property) {
        return property.getStatus() == PropertyStatus.ACTIVE
                || property.getStatus() == PropertyStatus.SOLD;
    }

    private boolean canViewPrivateProperty(Property property, String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            return false;
        }

        String normalizedEmail = authenticatedEmail.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalizedEmail)
                .map(user -> user.getRole() == Role.ADMIN
                        || (user.getRole() == Role.SELLER
                        && user.getId().equals(property.getSeller().getId())))
                .orElse(false);
    }

    private User findAuthenticatedUser(String authenticatedEmail) {
        String normalizedEmail = authenticatedEmail.trim().toLowerCase(Locale.ROOT);
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException(normalizedEmail));
    }

    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice != null && minPrice.signum() < 0) {
            throw new InvalidPropertyQueryException("minPrice cannot be negative");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new InvalidPropertyQueryException("maxPrice cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new InvalidPropertyQueryException("minPrice cannot be greater than maxPrice");
        }
    }
}
