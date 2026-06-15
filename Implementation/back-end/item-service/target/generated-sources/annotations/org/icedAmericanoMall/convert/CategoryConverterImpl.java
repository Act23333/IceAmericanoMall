package org.icedAmericanoMall.convert;

import javax.annotation.processing.Generated;
import org.icedAmericanoMall.domain.entity.CategoryEntity;
import org.icedAmericanoMall.domain.vo.CategoryTreeVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-06-15T14:49:24+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Ubuntu)"
)
@Component
public class CategoryConverterImpl implements CategoryConverter {

    @Override
    public CategoryTreeVO entityToTreeVO(CategoryEntity entity) {
        if ( entity == null ) {
            return null;
        }

        CategoryTreeVO categoryTreeVO = new CategoryTreeVO();

        categoryTreeVO.setId( entity.getId() );
        categoryTreeVO.setName( entity.getName() );
        categoryTreeVO.setSortOrder( entity.getSortOrder() );

        return categoryTreeVO;
    }
}
