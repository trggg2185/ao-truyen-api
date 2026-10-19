package com.example.mymangaapp.mymangaapp.enums;

// Khi trả về nhóm dịch mà user hiện tại đang tham gia sẽ có 4 trạng thái
public enum MyGroupStatus {
    LEADER, // trạng thái user là leader nhóm đó
    MEMBER, // user chỉ là thành viên
    PENDING_REQUEST, // user đã yêu cầu tạo nhóm hoặc tham gia nhóm đang chờ đc duyệt
    NONE // user chưa tham gia cũng chẳng gửi tạo nhóm hay tham gia nhóm nào cả
}
