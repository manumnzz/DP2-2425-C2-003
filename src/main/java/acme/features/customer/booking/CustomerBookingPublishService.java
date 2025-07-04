
package acme.features.customer.booking;

import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import acme.client.components.models.Dataset;
import acme.client.components.views.SelectChoices;
import acme.client.helpers.MomentHelper;
import acme.client.services.AbstractGuiService;
import acme.client.services.GuiService;
import acme.entities.S1.Flight;
import acme.entities.S1.Leg;
import acme.entities.S2.Booking;
import acme.entities.S2.ClassType;
import acme.realms.Customer;

@GuiService
public class CustomerBookingPublishService extends AbstractGuiService<Customer, Booking> {

	// Internal state ---------------------------------------------------------

	@Autowired
	private CustomerBookingRepository repository;

	// AbstractGuiService interface -------------------------------------------


	@Override
	public void authorise() {
		int bookingId = super.getRequest().getData("id", int.class);
		Booking booking = this.repository.findBookingById(bookingId);

		boolean status = booking != null && super.getRequest().getPrincipal().hasRealm(booking.getCustomer()) && booking.getDraftMode();

		super.getResponse().setAuthorised(status);
	}

	@Override
	public void load() {
		int bookingId = super.getRequest().getData("id", int.class);
		Booking booking = this.repository.findBookingById(bookingId);
		super.getBuffer().addData(booking);
	}

	@Override
	public void validate(final Booking booking) {
		// lastCreditCardNibble obligatorio
		if (!super.getBuffer().getErrors().hasErrors("lastCreditCardNibble"))
			if (booking.getLastCreditCardNibble() == null || booking.getLastCreditCardNibble().trim().isEmpty())
				super.state(false, "lastCreditCardNibble", "customer.booking.error.lastCreditCardNibble.required.");

		// Pasajeros publicados obligatorios
		int bookingId = booking.getId();
		int publishedPassengers = this.repository.countPublishedByBookingId(bookingId);
		int draftPassengers = this.repository.countDraftByBookingId(bookingId);

		if (publishedPassengers == 0)
			super.state(false, "passengers", "customer.booking.error.passengers.required");
		if (draftPassengers > 0)
			super.state(false, "passengers", "customer.booking.error.passengers.draftNotAllowed");
		// Verifica que el precio total del booking sea mayor que cero
		if (booking.getFlight() == null || booking.getFlight().getCost() == null || booking.getFlight().getCost().getAmount() <= 0)
			super.state(false, "price", "customer.booking.error.price.invalid");
		else {
			int passengerCount = this.repository.findPublishedByBookingId(booking.getId()).size();
			double totalAmount = booking.getFlight().getCost().getAmount() * passengerCount;
			super.state(totalAmount > 0.0, "price", "customer.booking.error.price.invalid");
		}

	}

	@Override
	public void perform(final Booking booking) {
		booking.setDraftMode(false);
		booking.setPurchaseMoment(MomentHelper.getCurrentMoment());

		this.repository.save(booking);
	}

	@Override
	public void bind(final Booking booking) {

	}

	@Override
	public void unbind(final Booking booking) {
		Collection<Flight> flights = this.repository.findAllFlights();
		SelectChoices flightChoices = new SelectChoices();

		for (Flight flight : flights) {
			List<Leg> legs = this.repository.findByFlightIdOrdered(flight.getId());
			if (!legs.isEmpty()) {
				Leg firstLeg = legs.get(0);
				Leg lastLeg = legs.get(legs.size() - 1);

				String origin = firstLeg.getDepartureAirport().getIataCode();
				String destination = lastLeg.getArrivalAirport().getIataCode();
				String departure = firstLeg.getScheduledDeparture().toString();
				String arrival = lastLeg.getScheduledArrival().toString();

				String label = origin + " – " + destination + " (" + departure + " – " + arrival + ")";
				boolean selected = booking.getFlight() != null && booking.getFlight().getId() == flight.getId();
				flightChoices.add(String.valueOf(flight.getId()), label, selected);
			}
		}

		Dataset dataset = super.unbindObject(booking, "locatorCode", "purchaseMoment", "travelClass", "lastCreditCardNibble", "draftMode", "flight");
		dataset.put("flights", flightChoices);

		// travelClass como SelectChoices
		SelectChoices travelClassChoices = SelectChoices.from(ClassType.class, booking.getTravelClass());
		dataset.put("travelClass", travelClassChoices);

		// lastCreditCardNibble como String o vacío
		dataset.put("lastCreditCardNibble", booking.getLastCreditCardNibble() != null ? booking.getLastCreditCardNibble() : "");

		// purchaseMoment como String o vacío
		dataset.put("purchaseMoment", booking.getPurchaseMoment() != null ? booking.getPurchaseMoment().toString() : "");

		// id y version
		dataset.put("id", booking.getId());
		dataset.put("version", booking.getVersion());

		super.getResponse().addData(dataset);
	}
}
