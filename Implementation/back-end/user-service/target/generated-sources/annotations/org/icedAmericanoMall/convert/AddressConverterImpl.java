package org.icedAmericanoMall.convert;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.icedAmericanoMall.domain.entity.AddressEntity;
import org.icedAmericanoMall.domain.vo.AddressResp;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-15T14:44:51+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Ubuntu)"
)
public class AddressConverterImpl implements AddressConverter {

    @Override
    public AddressResp map(AddressEntity entity) {
        if ( entity == null ) {
            return null;
        }

        AddressResp addressResp = new AddressResp();

        addressResp.setId( entity.getId() );
        addressResp.setUserId( entity.getUserId() );
        addressResp.setReceiver( entity.getReceiver() );
        addressResp.setPhone( entity.getPhone() );
        addressResp.setProvince( entity.getProvince() );
        addressResp.setCity( entity.getCity() );
        addressResp.setDistrict( entity.getDistrict() );
        addressResp.setStreet( entity.getStreet() );
        addressResp.setDetail( entity.getDetail() );
        addressResp.setDefaulted( entity.getDefaulted() );
        addressResp.setLabel( entity.getLabel() );
        addressResp.setLongitude( entity.getLongitude() );
        addressResp.setLatitude( entity.getLatitude() );
        addressResp.setCreateTime( entity.getCreateTime() );
        addressResp.setUpdateTime( entity.getUpdateTime() );

        return addressResp;
    }

    @Override
    public List<AddressResp> mapList(List<AddressEntity> list) {
        if ( list == null ) {
            return null;
        }

        List<AddressResp> list1 = new ArrayList<AddressResp>( list.size() );
        for ( AddressEntity addressEntity : list ) {
            list1.add( map( addressEntity ) );
        }

        return list1;
    }
}
