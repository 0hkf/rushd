package com.rushd.service;

import com.rushd.dto.CreatePropertyRequest;
import com.rushd.dto.PropertyResponse;
import com.rushd.entity.Property;
import com.rushd.entity.PropertyFacade;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.Role;
import com.rushd.entity.User;
import com.rushd.exception.UserNotFoundException;
import com.rushd.mapper.PropertyMapper;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        String normalizedEmail = authenticatedEmail.trim().toLowerCase(Locale.ROOT);
        User seller = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException(normalizedEmail));

        if (seller.getRole() != Role.SELLER) {
            throw new AccessDeniedException("Only sellers can create properties");
        }

        Property property = new Property();
        property.setSeller(seller);
        property.setTitle(request.getTitle().trim());
        property.setType(request.getType());
        property.setCity(request.getCity().trim());
        property.setDistrict(request.getDistrict().trim());
        property.setArea(request.getArea());
        property.setPrice(request.getPrice());
        property.setStreetWidth(request.getStreetWidth());
        property.setFacade(request.getFacade() == null
                ? PropertyFacade.UNKNOWN
                : request.getFacade());
        property.setPurpose(request.getPurpose());
        property.setDescription(request.getDescription());
        property.setStatus(request.getStatus() == null
                ? PropertyStatus.ACTIVE
                : request.getStatus());

        return PropertyMapper.toResponse(propertyRepository.save(property));
    }
}
