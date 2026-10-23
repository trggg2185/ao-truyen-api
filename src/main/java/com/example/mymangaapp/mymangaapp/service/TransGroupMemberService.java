package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.constant.RoleConstants;
import com.example.mymangaapp.mymangaapp.entity.Role;
import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import com.example.mymangaapp.mymangaapp.entity.User;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.repository.RoleRepository;
import com.example.mymangaapp.mymangaapp.repository.TransGroupRepository;
import com.example.mymangaapp.mymangaapp.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
// Quản lý sub resource là members của transgroup
public class TransGroupMemberService {

    TransGroupRepository transGroupRepository;
    UserRepository userRepository;
    RoleRepository roleRepository;

    @Transactional
    @PreAuthorize("@groupSec.isGroupLeader(#groupId)") // user hiện tại phải là leader nhóm này
    public void kickMember(String groupId, String memberId) {

        TransGroup transGroup = transGroupRepository
                .findWithMembersById(groupId)
                .orElseThrow(() -> new AppException(ResponseCode.TRANSGROUP_NOT_FOUND));

        User member = userRepository
                .findWithRolesById(memberId)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        // check user bị kick phải là member nhóm này
        if (!transGroupRepository.isMemberOfGroup(groupId, memberId)) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }

        // Xoá member khỏi group
        transGroup.getMembers().remove(member);
        // set group ở user null
        member.setTransGroup(null);

        Role translatorRole = roleRepository
                .findById(RoleConstants.TRANSLATOR)
                .orElseThrow(() -> new AppException(ResponseCode.ROLE_NOT_FOUND));

        member.getRoles().remove(translatorRole);

        transGroupRepository.save(transGroup);
        userRepository.save(member);
    }
}
