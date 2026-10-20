#!/usr/bin/env bash
cd Implementation/back-end || exit 1
for c in AddressClient AfterSaleClient AuthContextClient BalanceClient CartClient \
         FreightTemplateClient KnowledgeVectorClient LogisticsClient NotificationClient \
         OrderClient PointsClient RefundClient UserClient UserProfileClient \
         VerificationClient PromotionClient MemberClient TaskClient NoteVectorClient \
         GroupBuyClient GroupBuyTeamClient OrderRefundClient KgClient TradeInvoiceClient \
         CurrencyClient CouponClient SearchClient SkuClient SpuVectorClient \
         ItemSearchClient SpuDetailClient ; do
  echo "=================== $c ==================="
  grep -rn --include=*.java "\b$c\b" --exclude-dir=ia-api . | grep -v '/src/test/' | grep -v "import " || echo "   (no main callers)"
done
