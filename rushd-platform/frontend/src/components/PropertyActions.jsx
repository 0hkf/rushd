import { Link } from 'react-router-dom'
import FavoriteButton from './FavoriteButton'
import ComparisonButton from './ComparisonButton'
import Icon from './Icon'
import './PropertyActions.css'

/** One layout for card actions; behavior remains owned by the existing controls. */
export default function PropertyActions({ property, propertyId = property?.id, publicListing = false, showDetails = true, detailsState, removeOnly = false }) {
  return <div className="property-actions" role="group" aria-label="إجراءات العقار">
    {showDetails && property && <Link to={`/properties/${propertyId}`} state={detailsState} className="button button-primary property-details-action">
      عرض المواصفات <Icon name="arrow" />
    </Link>}
    <div className="property-actions-secondary">
      <FavoriteButton property={property} propertyId={propertyId} publicListing={publicListing} removeOnly={removeOnly} />
      {property && <ComparisonButton property={property} publicListing={publicListing} />}
    </div>
  </div>
}
