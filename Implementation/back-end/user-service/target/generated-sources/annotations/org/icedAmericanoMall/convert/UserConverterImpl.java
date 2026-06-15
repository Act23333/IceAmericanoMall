package org.icedAmericanoMall.convert;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.icedAmericanoMall.domain.entity.UserEntity;
import org.icedAmericanoMall.domain.vo.UserInfoResp;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-15T23:59:30+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Ubuntu)"
)
public class UserConverterImpl implements UserConverter {

    @Override
    public UserInfoResp map(UserEntity entity) {
        if ( entity == null ) {
            return null;
        }

        UserInfoResp userInfoResp = new UserInfoResp();

        userInfoResp.setUserId( entity.getUserId() );
        userInfoResp.setUsername( entity.getUsername() );
        userInfoResp.setPhone( entity.getPhone() );
        userInfoResp.setAvatar( entity.getAvatar() );
        userInfoResp.setStatus( map( entity.getStatus() ) );
        userInfoResp.setRegisterTime( entity.getRegisterTime() );
        userInfoResp.setBalance( entity.getBalance() );

        return userInfoResp;
    }

    @Override
    public List<UserInfoResp> mapList(List<UserEntity> list) {
        if ( list == null ) {
            return null;
        }

        List<UserInfoResp> list1 = new ArrayList<UserInfoResp>( list.size() );
        for ( UserEntity userEntity : list ) {
            list1.add( map( userEntity ) );
        }

        return list1;
    }
}
