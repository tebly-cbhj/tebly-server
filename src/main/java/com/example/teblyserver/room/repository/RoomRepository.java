package com.example.teblyserver.room.repository;

import com.example.teblyserver.room.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

}
